#pragma once
#define _GNU_SOURCE

#if !defined(SLAVE_AMD64) && !defined(SLAVE_RISCV)
#error "Unsupported architecture"
#endif

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include <sys/mman.h>
#include <setjmp.h>
// perror
#include <stdio.h>
#include <unistd.h>
#include <signal.h>


#if defined(SLAVE_AMD64)
#ifdef __AVX512F__
#define EVEX
#define AVX_REGISTERS 32
#else
#define AVX_REGISTERS 16
#endif
#endif

typedef struct {
    void *trampoline_code;
    void *scratch1;
    void *scratch2;

    int scratch_pkey;
} trampoline_t;

struct saved_state {
#ifdef SLAVE_AMD64
    uint64_t fs_base, gs_base, rax, rbx, rcx, rdx, rsi, rdi, rbp, r8, r9, r10, r11, r12, r13, r14, r15, rsp;
    uint8_t zmm [32][64];
    uint64_t mm[8];
    uint64_t rflags;
#elif defined(SLAVE_RISCV)
    uint64_t regs[32];
    uint64_t fregs[32];
#else
#error "Unsupported architecture"
#endif
}  __attribute__((aligned(16)));

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

void *trampoline_return_address(void);
void do_test(uint8_t *code, size_t code_length, struct execution_result *result);
void* maybe_allocate_signal_stack(void);