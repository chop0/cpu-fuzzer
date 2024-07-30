#include "slave.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <setjmp.h>
#include <signal.h>
#include <stdint.h>
#include <sys/mman.h>
#include <stdbool.h>
#include <fenv.h>
#include <sys/types.h>
#include <dirent.h>
#include <setjmp.h>
#include <immintrin.h>
#include <stdatomic.h>


#include <pthread.h>

#define thread_local _Thread_local
#define SIGNAL_STACK_SIZE (1048576)

#define TRAMPOLINE_SIZE ((size_t)((void*)&routine_end - (void*)&routine_begin))
#define TRAMPOLINE_START_OFFSET ((void *)&test_case_entry - (void*)&routine_begin)
#define TRAMPOLINE_FINISH_OFFSET ((void *)&test_case_exit - (void*)&routine_begin)
#define TRAMPOLINE_PKRU_ENTRY_OFFSET ((void *)&pkru_value_entry - (void*)&routine_begin)

#define MAX_THREADS 96

void* signal_stack_region;

void test_case_entry(void *code, struct saved_state *saved_state);
void test_case_exit(void);

thread_local int my_thread_idx = -1;

 int ignored_signals[] = { SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGTRAP };
#define NUM_IGNORED_SIGNALS (sizeof(ignored_signals) / sizeof(ignored_signals[0]))
 void (*java_signal_handlers[NUM_IGNORED_SIGNALS])(int,  siginfo_t *, void *);
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

#define assert(x) if (!(x)) { fprintf(stderr, "%s:%d: Assertion failed: %s\n", __FILE__, __LINE__, #x); exit(EXIT_FAILURE); }

typedef  struct {
			void *fs_base;
			void *gs_base;
			void *trampoline_address;
			void *sigstk_address;

			bool taken;
			bool active;
			bool faulted;
			struct fault_details fault_details;
			sigjmp_buf jmpbuf;
		} thread_info_t;

thread_info_t active_thread_info [MAX_THREADS] = {0};

void signal_handler(int signal, siginfo_t *si, void *ptr) {
	void *rsp = NULL;
	asm volatile("mov %%rsp, %0" : "=r"(rsp));

	int offset = (void *)rsp - signal_stack_region;
	uint64_t index = offset / SIGNAL_STACK_SIZE;

	if ((index < MAX_THREADS) && active_thread_info[index].active) {
		_writefsbase_u64((uint64_t) active_thread_info[index].fs_base);
		_writegsbase_u64((uint64_t) active_thread_info[index].gs_base);

		assert(index == my_thread_idx);
		active_thread_info[index].faulted = true;
		active_thread_info[index].fault_details = (struct fault_details) {
			.fault_address = si->si_addr,
			.fault_reason = si->si_signo,
			.fault_code = si->si_code
		};

		siglongjmp(active_thread_info[index].jmpbuf, 1);
	} else {
		for (int i = 0; i < NUM_IGNORED_SIGNALS; i++) {
			if (signal == ignored_signals[i]) {
				java_signal_handlers[i](signal, si, ptr);
				return;
			}
		}

		fprintf(stderr, "Unexpected signal %s\n", strsignal(signal));
		abort();
	}
}

static void setup_signal_handlers(void) {
	for (int i = 0; i < NUM_IGNORED_SIGNALS; i++) {
		struct sigaction sa = {0};

		if (sigaction(ignored_signals[i], NULL, &sa) == -1) {
					perror("sigaction");
					exit(EXIT_FAILURE);
		}

		java_signal_handlers[i] = sa.sa_sigaction;

		sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
		sa.sa_sigaction = signal_handler;
		if (sigaction(ignored_signals[i], &sa, NULL) == -1) {
			perror("sigaction");
			exit(EXIT_FAILURE);
		}
	}
}

static void maybe_cpu_fuzzer_setup(void) {
	static atomic_flag initialized = ATOMIC_FLAG_INIT;
	if (atomic_flag_test_and_set(&initialized)) {
		return;
	}

	signal_stack_region = (void *) 0x8f0000000;

	for (size_t i = 0; i < MAX_THREADS; i++) {
		void *address = mmap((uint8_t*)signal_stack_region + (size_t)(i * SIGNAL_STACK_SIZE), SIGNAL_STACK_SIZE, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED, -1, 0);
		if (address == MAP_FAILED) {
			perror("mmap");
			exit(EXIT_FAILURE);
		}

		active_thread_info[i].sigstk_address = address;
	}

	setup_signal_handlers();
}

void JVM_OnLoad(void) {
	maybe_cpu_fuzzer_setup();
}

thread_local static bool thread_init_completed = false;
void* maybe_allocate_signal_stack(void) {
	assert(__builtin_ia32_rdfsbase64());

	if (thread_init_completed) {
		return NULL;
	 }

	assert(pthread_mutex_lock(&mutex) == 0);
	maybe_cpu_fuzzer_setup();
	for (int i = 0; i < MAX_THREADS; i++) {
		if (!active_thread_info[i].taken) {
			active_thread_info[i].taken = true;
			my_thread_idx = i;
			break;
		}
	}
	assert(my_thread_idx != -1);
	pthread_mutex_unlock(&mutex);
	printf("allocated %d\n", my_thread_idx);

	stack_t ss;
	ss.ss_sp = active_thread_info[my_thread_idx].sigstk_address;
	assert(ss.ss_sp);
	ss.ss_size = SIGNAL_STACK_SIZE /2;
	ss.ss_flags = 0;
	if (sigaltstack(&ss, NULL) == -1) {
		perror("sigaltstack");
		exit(EXIT_FAILURE);
	}

	thread_init_completed = true;
	return &active_thread_info[my_thread_idx];
}

void do_test( void (*trampoline)(void), uint8_t *code, size_t code_length, struct execution_result *output) {
	assert(thread_init_completed);

	active_thread_info[my_thread_idx].fs_base = (void *)__builtin_ia32_rdfsbase64();
	active_thread_info[my_thread_idx].gs_base =(void *) __builtin_ia32_rdgsbase64();
	active_thread_info[my_thread_idx].active = true;
	active_thread_info[my_thread_idx].trampoline_address = trampoline + TRAMPOLINE_FINISH_OFFSET;

	void (*trampoline_func)(void *, struct saved_state *) = (void *)trampoline + TRAMPOLINE_START_OFFSET;

	active_thread_info[my_thread_idx].faulted = false;

   	if (sigsetjmp(active_thread_info[my_thread_idx].jmpbuf, 1) == 0) {
   		trampoline_func(code, &output->state);
   		siglongjmp(active_thread_info[my_thread_idx].jmpbuf, 1);
	}
	output->faulted = active_thread_info[my_thread_idx].faulted;
	if (output->faulted) {
		memcpy(&output->fault, &active_thread_info[my_thread_idx].fault_details, sizeof(output->fault));
		output->faulted = true;
	}

	active_thread_info[my_thread_idx].active = false;
}