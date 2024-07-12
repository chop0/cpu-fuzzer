#define _GNU_SOURCE

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include <sys/mman.h>
#include <setjmp.h>
// perror
#include <stdio.h>
#include <unistd.h>
#include <signal.h>

#ifdef __AVX512F__
#define EVEX
#define AVX_REGISTERS 32
#else
#define AVX_REGISTERS 16
#endif

void routine_begin(void);
void routine_end(void);

typedef struct {
    void *trampoline_code;
    void *scratch1;
    void *scratch2;

    int scratch_pkey;
} trampoline_t;

struct saved_state {
    uint64_t fs_base, gs_base, rax, rbx, rcx, rdx, rsi, rdi, rbp, r8, r9, r10, r11, r12, r13, r14, r15, rsp;
    uint8_t zmm[64][32];
    uint64_t mm[8];
    uint64_t rflags;
};

struct fault_details {
    void *fault_address;
    int fault_reason;
    int fault_code;
};

struct execution_result {
    bool faulted;

    union {
        struct fault_details fault;
        struct saved_state state;
    };
};

void do_test( void (*trampoline)(void), uint8_t *code, size_t code_length, struct execution_result *result);
void test_case_exit(void);