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
#define TRAMPOLINE_PKRU_EXIT_OFFSET ((void *)&pkru_value_exit - (void*)&routine_begin)

#define MAX_THREADS 96

static int signal_stack_pkey;

static void* signal_stack_region;

void test_case_entry(void *code, struct saved_state *saved_state);
void test_case_exit(void);
extern uint32_t pkru_value_entry, pkru_value_exit;

thread_local int my_thread_idx = -1;

thread_local static struct fault_details fault_details = {0};
thread_local static bool faulted = false;

static int ignored_signals[] = { SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGTRAP };
#define NUM_IGNORED_SIGNALS (sizeof(ignored_signals) / sizeof(ignored_signals[0]))
static void (*java_signal_handlers[NUM_IGNORED_SIGNALS])(int,  siginfo_t *, void *);
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
            bool taken;
            bool active;
            void *fs_base;
            void *gs_base;
            void *trampoline_address;
            void *sigstk_address;
        } thread_info_t;

static thread_info_t volatile active_thread_info [MAX_THREADS] = {0};

static void handle_java_signal(int signal, siginfo_t *si, void *ptr) {
      int handler_index = -1;
    for (int i = 0; i < MAX_THREADS; i++) {
        if (signal == ignored_signals[i]) {
            handler_index = i;
            break;
        }
    }

    if (handler_index == -1) {
        fprintf(stderr, "Unexpected signal %d\n", signal);
        exit(EXIT_FAILURE);
    }

    java_signal_handlers[handler_index](signal, si, ptr);
    return;
}

static void _segfault_handler(int signal, siginfo_t *si, void *ptr) {
    void *rsp = asm_get_rsp();
    assert(signal_stack_region);
    int thread_idx = (rsp - signal_stack_region) / SIGSTKSZ;

    if (!active_thread_info[thread_idx].active) {
        handle_java_signal(signal, si, ptr);
        return;
    }

    asm_set_fs_base(active_thread_info[thread_idx].fs_base);
    asm_set_gs_base(active_thread_info[thread_idx].gs_base);
    if (thread_idx != my_thread_idx) {
        printf("Index mismatch: thread_idx = %d, my_idx = %d\n", thread_idx, my_thread_idx);
    }

    faulted = true;
    fault_details = (struct fault_details){si->si_signo, si->si_code, (void*)(si->si_addr)};

    void (*fin)(void) = active_thread_info[thread_idx].trampoline_address + TRAMPOLINE_FINISH_OFFSET;
    fin();
}

__attribute__ ((naked))
static void segfault_handler() {
    asm volatile(
                        "xor %%eax, %%eax\n\t"
                        "xor %%ecx, %%ecx\n\t"
                        "xor %%edx, %%edx\n\t"
                        "wrpkru\n\t"
                     :
                     :
                     : "rax", "rcx", "rdx");
    asm volatile("jmp *%0" : : "r"(_segfault_handler));
}

static uint32_t get_pkru_value(void) {
    uint32_t pkru;
    asm volatile("xor %%ecx, %%ecx\n\t"
                    "rdpkru\n\t"
                    : "=a"(pkru)
                    :
                    : "ecx");
    return pkru;
}

static void setup_signal_handlers(void) {
    for (int i = 0; i < NUM_IGNORED_SIGNALS; i++) {
        struct sigaction sa = {0};

        if (sigaction(ignored_signals[i], NULL, &sa) == -1) {
                    perror("sigaction");
                    exit(EXIT_FAILURE);
        }

        java_signal_handlers[i] = sa.sa_sigaction;

        sa.sa_flags |= SA_SIGINFO | SA_NODEFER | SA_ONSTACK;
        sa.sa_sigaction = segfault_handler;
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
        active_thread_info[i].sigstk_address = signal_stack_region + 2 * i * SIGSTKSZ;
    }

    setup_signal_handlers();
}

void do_test(int scratch_pkey, void (*trampoline)(void), uint8_t *code, size_t code_length, struct execution_result *output) {
    static bool volatile global_initialized = false;
    if (!global_initialized) {
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
    uint32_t *exit_pkru = (uint32_t *)(trampoline + TRAMPOLINE_PKRU_EXIT_OFFSET);
    for (int i = 0; i < 16; i++) {
        if (i != scratch_pkey && i != signal_stack_pkey) {
            int write_disable_bit = 2 * i + 1;
            *entry_pkru |= 1 << write_disable_bit;
        }
    }

    *exit_pkru = get_pkru_value();

    void (*trampoline_func)(void *, struct saved_state *) = (void *)trampoline + TRAMPOLINE_START_OFFSET;

    faulted = false;

    trampoline_func(code, &output->state);
    output->faulted = faulted;
    if (faulted) {
        memcpy(&output->fault, &fault_details, sizeof(output->fault));
        output->faulted = true;
    }

    active_thread_info[my_thread_idx].active = false;
}