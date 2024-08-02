#ifndef SLAVE_PRIVATE_H
#define SLAVE_PRIVATE_H

#include "slave.h"

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#include <setjmp.h>

#define TRAMPOLINES_BEGIN ((void *) 0x9f0000000)
#define ALT_STACKS_BEGIN ((void *) 0xaf0000000)

#define ALT_STACK_SIZE (1048576)
#define TEST_CASE_TIMEOUT_NS (1000000)

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
#define assert(x) if (!(x)) { fprintf(stderr, "%s:%d: Assertion failed: %s\n", __FILE__, __LINE__, #x); exit(EXIT_FAILURE); }

#define THREAD_IDX_MAX 96

typedef void trampoline_entrypoint_t(void *code, struct saved_state *saved_state);

extern trampoline_entrypoint_t trampoline_entrypoint;
extern uint8_t trampoline_exitpoint;
extern uint8_t trampoline_begin;
extern uint8_t trampoline_end;

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

#endif