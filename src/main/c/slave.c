#include "slave.h"
#include "slave_private.h"

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
#include <fcntl.h>
#include <sys/timerfd.h>
#include <sys/syscall.h>

#include <pthread.h>

static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static int ignored_signals[] = { SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGTRAP, SIGALRM };
static void (*java_signal_handlers[NUM_IGNORED_SIGNALS])(int,  siginfo_t *, void *);

static void* signal_stack_region;

thread_local int my_thread_idx = -1;
thread_local int my_timer_fd = NULL;

thread_local uint8_t *debug_file_address = NULL;

thread_info_t active_thread_info [THREAD_IDX_MAX] = {0};

static int tkill(int tid, int sig) {
	return syscall(SYS_tkill, tid, sig);
}

static void __timer_arm(void) {
	static struct itimerspec const test_case_timeout = { .it_value.tv_nsec = TEST_CASE_TIMEOUT_NS };

	pthread_mutex_lock(&active_thread_info[my_thread_idx].mutex);
	if (timerfd_settime(my_timer_fd, 0, &test_case_timeout, NULL) != 0) {
		perror("__timer_arm: timer_settime");
		abort();
	}
	active_thread_info[my_thread_idx].active = true;
	pthread_mutex_unlock(&active_thread_info[my_thread_idx].mutex);
}

static void __timer_disarm(void) {
	static struct itimerspec const test_case_timeout = { .it_value.tv_nsec = 0 };

	pthread_mutex_lock(&active_thread_info[my_thread_idx].mutex);
	if (timerfd_settime(my_timer_fd, 0, &test_case_timeout, NULL) != 0) {
		perror("__timer_arm: timer_settime");
		abort();
	}
	active_thread_info[my_thread_idx].active = false;
	pthread_mutex_unlock(&active_thread_info[my_thread_idx].mutex);
}

static void __construct_trampoline(int thread_idx) {
	if (mmap(TRAMPOLINE_LOCATION(thread_idx), TRAMPOLINE_SIZE_ALIGNED, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED, -1, 0) == MAP_FAILED) {
		perror("__construct_trampoline: mmap");
		exit(EXIT_FAILURE);
	}

	memcpy(TRAMPOLINE_LOCATION(thread_idx), &trampoline_begin, TRAMPOLINE_SIZE);
}

static void __construct_alt_stack(int thread_idx) {
	if (mmap(ALT_STACK_LOCATION(thread_idx), ALT_STACK_SIZE, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED, -1, 0) == MAP_FAILED) {
		perror("__construct_alt_stack: mmap");
		exit(EXIT_FAILURE);
	}
}

static void __signal_handler_jvm(int signal, siginfo_t *si, void *ptr) {
	for (int i = 0; i < NUM_IGNORED_SIGNALS; i++) {
		if (signal == ignored_signals[i]) {
			if (java_signal_handlers[i] == NULL) {
				fprintf(stderr, "Unexpected signal: %s\n", strsignal(signal));
				abort();
			}

			java_signal_handlers[i](signal, si, ptr);
			return;
		}
	}

	fprintf(stderr, "Unexpected signal %s\n", strsignal(signal));
	abort();
}

static void __save_segment_registers(int index) {
	active_thread_info[index].fs_base = (void *)__builtin_ia32_rdfsbase64();
	active_thread_info[index].gs_base =(void *) __builtin_ia32_rdgsbase64();
}

static void __restore_segment_registers(int index) {
	_writefsbase_u64((uint64_t) active_thread_info[index].fs_base);
	_writegsbase_u64((uint64_t) active_thread_info[index].gs_base);
}

struct signal_monitor_args {
	int fd;
	int tid;
	thread_info_t volatile* thread_info;
};

static void __signal_monitor(struct signal_monitor_args *args) {
	int fd = args->fd;
	int tid = args->tid;

	for (;;) {
		uint64_t exp;
		if (read(fd, &exp, sizeof(uint64_t)) == -1) {
			perror("read");
			exit(EXIT_FAILURE);
		}

		pthread_mutex_lock(&args->thread_info->mutex);
		if ((exp > 0) && args->thread_info->active) {
			if (tkill(tid, SIGALRM) == -1) {
				perror("tkill");
				exit(EXIT_FAILURE);
			}
		}
		pthread_mutex_unlock(&args->thread_info->mutex);
	}
}

static void __create_signal_monitor(int tid, int fd) {
	pthread_t thread;
	struct signal_monitor_args *packed_args = malloc(sizeof(struct signal_monitor_args));
	if (packed_args == NULL) {
		perror("malloc");
		exit(EXIT_FAILURE);
	}

	packed_args->fd = fd;
	packed_args->tid = tid;
	packed_args->thread_info = &active_thread_info[my_thread_idx];

	if (pthread_create(&thread, NULL, (void *(*)(void *)) __signal_monitor, (void *)packed_args) != 0) {
		perror("pthread_create");
		exit(EXIT_FAILURE);
	}

	if (pthread_detach(thread) != 0) {
		perror("pthread_detach");
		exit(EXIT_FAILURE);
	}
}

static int __alt_stack_index(void) {
	void *rsp = NULL;
	asm volatile("mov %%rsp, %0" : "=r"(rsp));

	int idx = ALT_STACK_IDX_FROM_LOCATION(rsp);
	if (idx >= 0 && idx < THREAD_IDX_MAX) {
		return idx;
	}

	return -1;
}

void *trampoline_return_address(void) {
	return TRAMPOLINE_EXITPOINT(my_thread_idx);
}

void signal_handler(int signal, siginfo_t *si, void *ptr) {
	int index = __alt_stack_index();

	if (index == -1) {
		__signal_handler_jvm(signal, si, ptr);
		return;
	}

	if (!active_thread_info[index].active) {
		if (signal != SIGALRM)
			__signal_handler_jvm(signal, si, ptr);
		return;
	}

	__restore_segment_registers(index);

	assert(index == my_thread_idx);
	active_thread_info[index].faulted = true;
	active_thread_info[index].fault_details = (struct fault_details) {
		.fault_address = si->si_addr,
		.fault_reason = signal,
		.fault_code = si->si_code
	};

	siglongjmp(active_thread_info[index].jmpbuf, 1);
}

static void maybe_cpu_fuzzer_setup(void) {
	static atomic_flag initialized = ATOMIC_FLAG_INIT;
	if (atomic_flag_test_and_set(&initialized)) {
		return;
	}

	for (size_t i = 0; i < THREAD_IDX_MAX; i++) {
		__construct_trampoline(i);
		__construct_alt_stack(i);
	}

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

thread_local static bool thread_init_completed = false;
void* maybe_allocate_signal_stack(void) {
	assert(__builtin_ia32_rdfsbase64());

	if (thread_init_completed) {
		return NULL;
	 }

	assert(pthread_mutex_lock(&mutex) == 0);
	maybe_cpu_fuzzer_setup();
	for (int i = 0; i < THREAD_IDX_MAX; i++) {
		if (!active_thread_info[i].taken) {
			active_thread_info[i].taken = true;
			pthread_mutex_init(&active_thread_info[i].mutex, NULL);
			my_thread_idx = i;
			break;
		}
	}
	assert(my_thread_idx != -1);
	pthread_mutex_unlock(&mutex);

	stack_t ss = { .ss_sp = ALT_STACK_LOCATION(my_thread_idx), .ss_size = ALT_STACK_SIZE, .ss_flags = 0 };
	if (sigaltstack(&ss, NULL) == -1) {
		perror("sigaltstack");
		exit(EXIT_FAILURE);
	}

	if ((my_timer_fd = timerfd_create(CLOCK_REALTIME, NULL)) == -1) {
		perror("timer_create");
		exit(EXIT_FAILURE);
	}

	__timer_disarm();
	__create_signal_monitor(gettid(), my_timer_fd);

	char debug_file_path[64];
	sprintf(debug_file_path, "./dbg_thread_%d.txt", my_thread_idx);
	int debug_file_fd = open(debug_file_path, O_CREAT | O_RDWR, 0644);
	if (debug_file_fd == -1) {
		perror("open");
		exit(EXIT_FAILURE);
	}

	debug_file_address = mmap(NULL, 4096, PROT_READ | PROT_WRITE, MAP_SHARED, debug_file_fd, 0);
	if (debug_file_address == MAP_FAILED) {
		perror("mmap");
		exit(EXIT_FAILURE);
	}
	if (ftruncate(debug_file_fd, 4096) == -1) {
		perror("ftruncate");
		exit(EXIT_FAILURE);
	}

	thread_init_completed = true;
	return &active_thread_info[my_thread_idx];
}

static void print_code(uint8_t *code, size_t code_length) {
	for (size_t i = 0; i < code_length; i++) {
		printf("%02x ", code[i]);
	}
	printf("\n");
	fflush(stdout);
}

void do_test(uint8_t *code, size_t code_length, struct execution_result *output) {
	assert(thread_init_completed);
	assert(my_thread_idx >= 0);
	assert(my_thread_idx <= THREAD_IDX_MAX);

	__save_segment_registers(my_thread_idx);

	trampoline_entrypoint_t *entrypoint = TRAMPOLINE_ENTRYPOINT(my_thread_idx);
	active_thread_info[my_thread_idx].faulted = false;

	*(uint64_t volatile*) debug_file_address = code_length;
	memcpy(debug_file_address + 8, code, code_length);

	__timer_arm();
   	if (sigsetjmp(active_thread_info[my_thread_idx].jmpbuf, 1) == 0) {
   		entrypoint(code, &output->state);
   		siglongjmp(active_thread_info[my_thread_idx].jmpbuf, 1);
	}
	__timer_disarm();

	*(uint64_t volatile*) debug_file_address = 0;

	output->faulted = active_thread_info[my_thread_idx].faulted;
	if (output->faulted) {
		output->fault = active_thread_info[my_thread_idx].fault_details;
	}
}