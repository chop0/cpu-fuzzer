#pragma once
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


typedef struct {
    void *trampoline_code;
    void *scratch1;
    void *scratch2;

    int scratch_pkey;
} trampoline_t;

struct saved_state {
    uint64_t fs_base, gs_base, rax, rbx, rcx, rdx, rsi, rdi, rbp, r8, r9, r10, r11, r12, r13, r14, r15, rsp;
    uint8_t zmm [32][64];
    uint64_t mm[8];
    uint64_t rflags;
}  __attribute__((aligned(16)));

#define SAVED_STATE_QFIELDS(V) \
    V(fs_base) \
    V(gs_base) \
    V(rax) \
    V(rbx) \
    V(rcx) \
    V(rdx) \
    V(rsi) \
    V(rdi) \
    V(rbp) \
    V(r8) \
    V(r9) \
    V(r10) \
    V(r11) \
    V(r12) \
    V(r13) \
    V(r14) \
    V(r15) \
    V(rsp)

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