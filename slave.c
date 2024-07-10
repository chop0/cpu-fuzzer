#include "slave.h"
#include "memory_mappings.h"

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

#include <pthread.h>
#include <threads.h>

#define SIGSTKSZ 8192

#define TRAMPOLINE_SIZE ((size_t)((void*)&routine_end - (void*)&routine_begin))
#define TRAMPOLINE_START_OFFSET ((void *)&test_case_entry - (void*)&routine_begin)
#define TRAMPOLINE_FINISH_OFFSET ((void *)&test_case_exit - (void*)&routine_begin)
#define TRAMPOLINE_PKRU_ENTRY_OFFSET ((void *)&pkru_value_entry - (void*)&routine_begin)

#define MAX_THREADS 96

static int signal_stack_pkey;

void* signal_stack_region;

void test_case_entry(void *code, struct saved_state *saved_state);
void test_case_exit(void);
extern uint32_t pkru_value_entry;

thread_local int my_thread_idx = -1;

thread_local sigjmp_buf jmpbuf;

 int ignored_signals[] = { SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGTRAP };
#define NUM_IGNORED_SIGNALS (sizeof(ignored_signals) / sizeof(ignored_signals[0]))
 void (*java_signal_handlers[NUM_IGNORED_SIGNALS])(int,  siginfo_t *, void *);
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

static void* asm_get_rsp(void) {
    void *rsp;
    asm volatile("movq %%rsp, %0" : "=r"(rsp));
    return rsp;
}

// use rdfsbase to get fs base
static void* asm_get_fs_base(void) {
    void *fs_base;
    asm volatile("rdfsbase %0" : "=r"(fs_base));
    return fs_base;
}

static void asm_set_fs_base(void *fs_base) {
    asm volatile("wrfsbase %0" : : "r"(fs_base));
}

static void* asm_get_gs_base(void) {
    void *gs_base;
    asm volatile("rdgsbase %0" : "=r"(gs_base));
    return gs_base;
}

static void asm_set_gs_base(void *gs_base) {
    asm volatile("wrgsbase %0" : : "r"(gs_base));
}


#define assert(x) if (!(x)) { fprintf(stderr, "%s:%d: Assertion failed: %s\n", __FILE__, __LINE__, #x); exit(EXIT_FAILURE); }
typedef  struct {
            void *fs_base;
            void *gs_base;
            void *trampoline_address;
            void *sigstk_address;
            void *jstack_address;
            uint32_t pkru_restore;

            bool taken;
            bool active;
            bool faulted;
            struct fault_details fault_details;
        } thread_info_t;

_Static_assert(sizeof(thread_info_t) ==64, "thread_info_t size mismatch");
thread_info_t volatile active_thread_info [MAX_THREADS] = {0};


void signal_handler(int signal, siginfo_t *si, void *ptr);

static void setup_signal_handlers(void) {
    for (int i = 0; i < NUM_IGNORED_SIGNALS; i++) {
        struct sigaction sa = {0};

        if (sigaction(ignored_signals[i], NULL, &sa) == -1) {
                    perror("sigaction");
                    exit(EXIT_FAILURE);
        }

        java_signal_handlers[i] = sa.sa_sigaction;

        sa.sa_flags |= SA_SIGINFO | SA_NODEFER | SA_ONSTACK;
        sa.sa_sigaction = signal_handler;
        if (sigaction(ignored_signals[i], &sa, NULL) == -1) {
            perror("sigaction");
            exit(EXIT_FAILURE);
        }
    }
}

static void allocate_signal_stack(void) {
    thread_local static bool thread_init_completed = false;
    assert(!thread_init_completed);

    pthread_mutex_lock(&mutex);
    for (int i = 0; i < MAX_THREADS; i++) {
        if (!active_thread_info[i].taken) {
            active_thread_info[i].taken = true;
            my_thread_idx = i;
            break;
        }
    }
    assert(my_thread_idx != -1);
    pthread_mutex_unlock(&mutex);

    stack_t ss;
    ss.ss_sp = active_thread_info[my_thread_idx].sigstk_address;
    ss.ss_size = SIGSTKSZ;
    ss.ss_flags = 0;
    if (sigaltstack(&ss, NULL) == -1) {
        perror("sigaltstack");
        exit(EXIT_FAILURE);
    }

    thread_init_completed = true;
}

static void cpu_fuzzer_setup(void) {
    signal_stack_region = mmap(NULL, 2 * SIGSTKSZ * MAX_THREADS, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    signal_stack_pkey = pkey_alloc(0, 0);

    if (signal_stack_pkey < 0) {
        perror("pkey_alloc");
        exit(EXIT_FAILURE);
    }

    for (int i = 0; i < MAX_THREADS; i++) {
        pkey_mprotect(signal_stack_region + 2 * i * SIGSTKSZ, SIGSTKSZ, PROT_READ | PROT_WRITE | PROT_EXEC, signal_stack_pkey);
        mprotect(signal_stack_region + (2 * i + 1) * SIGSTKSZ, SIGSTKSZ, PROT_READ | PROT_WRITE);
        active_thread_info[i].sigstk_address = signal_stack_region + 2 * i * SIGSTKSZ;
        active_thread_info[i].jstack_address = signal_stack_region + (2 * i + 1) * SIGSTKSZ;
    }

    setup_signal_handlers();
}

void do_test(int scratch_pkey, void (*trampoline)(void), uint8_t *code, size_t code_length, struct execution_result *output) {
    static bool volatile global_initialized = false;
    if (!global_initialized) {
        puts("Initializing global state");
        fflush(stdout);
        pthread_mutex_lock(&mutex);
        if (!global_initialized) {
            cpu_fuzzer_setup();
            global_initialized = true;
        }
        pthread_mutex_unlock(&mutex);
    }

    thread_local static bool initialized = false;
    if (!initialized) {
           allocate_signal_stack();
           initialized = true;
    }


    active_thread_info[my_thread_idx].fs_base = asm_get_fs_base();
    active_thread_info[my_thread_idx].gs_base = asm_get_gs_base();
    active_thread_info[my_thread_idx].active = true;
    active_thread_info[my_thread_idx].trampoline_address = trampoline;

    uint32_t *entry_pkru = (uint32_t *)(trampoline + TRAMPOLINE_PKRU_ENTRY_OFFSET);
    for (int i = 0; i < 16; i++) {
        if (i != scratch_pkey && i != signal_stack_pkey) {
            int write_disable_bit = 2 * i + 1;
            *entry_pkru |= 1 << write_disable_bit;
        }
    }

#define PKEY_WRITE_DISABLE(pkey) (1 << (2 * pkey + 1))
#define PKEY_ACCESS_DISABLE(pkey) (1 << (2 * pkey))

    void (*trampoline_func)(void *, struct saved_state *) = (void *)trampoline + TRAMPOLINE_START_OFFSET;

    active_thread_info[my_thread_idx].faulted = false;

    if (sigsetjmp(jmpbuf, 1) == 0) {
        trampoline_func(code, &output->state);
    }
    output->faulted = active_thread_info[my_thread_idx].faulted;
    if (output->faulted) {
        memcpy(&output->fault, &active_thread_info[my_thread_idx].fault_details, sizeof(output->fault));
        output->faulted = true;
    }

    active_thread_info[my_thread_idx].active = false;
}