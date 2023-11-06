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

#define CODE_SIZE 4096
#define CODE_REGION ((void*)0x41414000)

void jump_to_code(void *code, struct saved_state *saved_state);
void test_case_finish(void);

static struct fault_details fault_details = {0};
static bool faulted = false;

static uint8_t EPILOGUE[] = { 0xFF, 0x25, 0x00, 0x00, 0x00, 0x00 }; // jmpq *0x0

static int ignored_signals[] = { SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGTRAP };
#define NUM_IGNORED_SIGNALS (sizeof(ignored_signals) / sizeof(ignored_signals[0]))
static struct sigaction saved_sigactions[NUM_IGNORED_SIGNALS];

__attribute__ ((no_stack_protector))  // sicne we mess with fs
static void segfault_handler(int signal, siginfo_t *si, void *ptr) {
    faulted = true;
    fault_details = (struct fault_details){si->si_signo, si->si_code, (void*)(si->si_addr - CODE_REGION)};
    test_case_finish(); // doesnt return
}

static void setup_signal_handlers(void) {
    for (int i = 0; i < NUM_IGNORED_SIGNALS; i++) {
        struct sigaction sa;
        sa.sa_flags = SA_SIGINFO | SA_NODEFER | SA_ONSTACK | SA_RESETHAND;
        sigemptyset(&sa.sa_mask);
        sa.sa_sigaction = segfault_handler;
        if (sigaction(ignored_signals[i], &sa, NULL) == -1) {
            perror("sigaction");
            exit(EXIT_FAILURE);
        }
    }

}

static void restore_signal_handlers(void) {
    for (int i = 0; i < NUM_IGNORED_SIGNALS; i++) {
        if (sigaction(ignored_signals[i], &saved_sigactions[i], NULL) == -1) {
            perror("sigaction");
            exit(EXIT_FAILURE);
        }
        saved_sigactions[i] = (struct sigaction){0};
    }
}

static void save_signal_handlers(void) {
    for (int i = 0; i < NUM_IGNORED_SIGNALS; i++) {
        saved_sigactions[i] = (struct sigaction){0};
        if (sigaction(ignored_signals[i], NULL, &saved_sigactions[i]) == -1) {
            perror("sigaction");
            exit(EXIT_FAILURE);
        }
    }
}

void do_test(uint8_t *code, size_t code_length, struct execution_result *output) {
    static bool initialized = false;
    if (!initialized) {
            void *signal_stack = malloc(SIGSTKSZ);
            if (signal_stack == NULL) {
                perror("malloc failed");
                exit(EXIT_FAILURE);
            }
            stack_t ss;
            ss.ss_sp = signal_stack;
            ss.ss_size = SIGSTKSZ;
            ss.ss_flags = 0;
            if (sigaltstack(&ss, NULL) == -1) {
                perror("sigaltstack");
                exit(EXIT_FAILURE);
            }

           void *code_page = mmap(CODE_REGION, CODE_SIZE, PROT_READ | PROT_WRITE | PROT_EXEC,
                                  MAP_FIXED | MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);

           if (code_page == MAP_FAILED) {
               perror("mmap failed");
               exit(EXIT_FAILURE);
           }
           initialized = true;
    }

    save_signal_handlers();
    setup_signal_handlers();

    memcpy(CODE_REGION, code, code_length);
    memcpy(CODE_REGION + code_length, EPILOGUE, sizeof(EPILOGUE));
    *(void * volatile*) (CODE_REGION + code_length + sizeof(EPILOGUE)) = test_case_finish;

    faulted = false;

    jump_to_code(CODE_REGION, &output->state);
    output->faulted = faulted;
    if (faulted) {
        memcpy(&output->fault, &fault_details, sizeof(output->fault));
        output->faulted = true;
    }

    restore_signal_handlers();
}