#ifndef SLAVE_PRIVATE_H
#define SLAVE_PRIVATE_H

#include "slave.h"

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include <pthread.h>

#include <setjmp.h>

#define TRAMPOLINES_BEGIN ((void *) 0x9f0000000)
#define ALT_STACKS_BEGIN ((void *) 0xaf0000000)

#define ALT_STACK_SIZE (1048576)

#ifdef SLAVE_AMD64
#define TEST_CASE_TIMEOUT_SEC (0)
#define TEST_CASE_TIMEOUT_NS (1000000)
#elif defined(SLAVE_RISCV)
#define TEST_CASE_TIMEOUT_SEC (1)
#define TEST_CASE_TIMEOUT_NS (0)
#else
#error "Unsupported architecture"
#endif

#define thread_local _Thread_local

static size_t page_size;
__attribute__((constructor)) static void __init_page_size(void) {
	page_size = (size_t) sysconf(_SC_PAGESIZE);
}

#define PAGE_ALIGN_UP(x) (((x) + page_size - 1) & ~(page_size - 1))

#define TRAMPOLINE_SIZE ((size_t)((void*)&trampoline_end - (void*)&trampoline_begin))
#define TRAMPOLINE_SIZE_ALIGNED PAGE_ALIGN_UP(TRAMPOLINE_SIZE)
#define TRAMPOLINE_TEMPLATE_BASE ((void*)&trampoline_begin)

#define TRAMPOLINE_LOCATION(ID) (TRAMPOLINES_BEGIN + (ID) * TRAMPOLINE_SIZE_ALIGNED)
#define ALT_STACK_LOCATION(ID) (ALT_STACKS_BEGIN + (ID) * ALT_STACK_SIZE)
#define ALT_STACK_IDX_FROM_LOCATION(PTR) ((int)(((void *)(PTR) - ALT_STACKS_BEGIN) / ALT_STACK_SIZE))
#define TRAMPOLINE_ENTRYPOINT(ID) ((trampoline_entrypoint_t *)(TRAMPOLINE_LOCATION(ID) + ((void *)&trampoline_entrypoint - (void*)&trampoline_begin)))
#define TRAMPOLINE_EXITPOINT(ID) (TRAMPOLINE_LOCATION(ID) + ((void *)&trampoline_exitpoint - (void*)&trampoline_begin))

#define NUM_IGNORED_SIGNALS (sizeof(ignored_signals) / sizeof(ignored_signals[0]))
#define assert(x) if (!(x)) { fprintf(stderr, "%s:%d: Assertion failed: %s\n", __FILE__, __LINE__, #x); fflush(stderr); exit(EXIT_FAILURE); }

#ifndef THREAD_IDX_MAX
#define THREAD_IDX_MAX 96
#endif

typedef void trampoline_entrypoint_t(void *code, struct saved_state *saved_state);

extern trampoline_entrypoint_t trampoline_entrypoint;
extern uint8_t trampoline_exitpoint;
extern uint8_t trampoline_begin;
extern uint8_t trampoline_end;

#ifdef SLAVE_AMD64
typedef struct {
	void *fs_base;
	void *gs_base;
} critical_registers_t;

#elif defined(SLAVE_RISCV)
typedef struct {
	void *tp;
	void *gp;
} critical_registers_t;
#else
#error "Unsupported architecture"
#endif

typedef struct {
	critical_registers_t critical_registers;
	void *trampoline_address;
	void *sigstk_address;

	bool taken;
	bool active;
	bool faulted;
	struct fault_details fault_details;
	sigjmp_buf jmpbuf;
	pthread_mutex_t mutex;
} thread_info_t;

#endif