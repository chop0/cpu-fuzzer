#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>
#include <sys/mman.h>

#include <slave.h>

void *trampoline;
void *code;

typedef uint8_t byte;

uint8_t code_data[] = { 0x4D, 0x31, 0xFF, 0x49, 0x83, 0xFF, 0x64, 0x7D, 0x62, 0x49, 0xFF, 0xC7, 0x0F, 0x71, 0xF3, 0xD9, 0x67, 0xC5, 0x19, 0x7D, 0x24, 0x25, 0x10, 0x0A, 0x21, 0x00, 0x67, 0x66, 0x44, 0x0F, 0x5B, 0x14, 0x25, 0x20, 0x05, 0x11, 0x00, 0x48, 0x09, 0x34, 0x25, 0xB0, 0x01, 0x11, 0x00, 0x44, 0x0F, 0xBD, 0xCE, 0x67, 0x44, 0x8D, 0x24, 0x25, 0x00, 0x0E, 0x21, 0x00, 0x67, 0xC5, 0xF8, 0x52, 0x24, 0x25, 0x40, 0x09, 0x21, 0x00, 0x67, 0xC4, 0xE2, 0x59, 0xBB, 0x04, 0x25, 0x24, 0x02, 0x21, 0x00, 0x67, 0x62, 0x61, 0x64, 0x00, 0x12, 0x04, 0x25, 0x00, 0x00, 0x11, 0x00, 0x67, 0x62, 0x71, 0xFD, 0x08, 0x16, 0x04, 0x25, 0x58, 0x05, 0x21, 0x00, 0x71, 0x02, 0xEB, 0x00,
	0xFF, 0x25, 0x00, 0x00, 0x00, 0x00,
 	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

#define HEXFMT8 "%02x%02x%02x%02x%02x%02x%02x%02x"
#define HEXFMT32  HEXFMT8 HEXFMT8 HEXFMT8 HEXFMT8
#define HEXFMT64  HEXFMT32 HEXFMT32

#define HEXARGS8(x, offset) (x)[offset], (x)[offset+1], (x)[offset+2], (x)[offset+3], (x)[offset+4], (x)[offset+5], (x)[offset+6], (x)[offset+7]
#define HEXARGS32(x, offset) HEXARGS8(x, offset), HEXARGS8(x, offset+8), HEXARGS8(x, offset+16), HEXARGS8(x, offset+24)
#define HEXARGS64(x, offset) HEXARGS32(x, offset), HEXARGS32(x, offset+32)

void init() {
	char *result;
	result = mmap((void *)0x110000, 8192, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED, -1, 0);
	if (result == MAP_FAILED) {
		result = "mmap: scratch1";
		goto fail;
	}

	result = mmap((void *)0x210000, 8192, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED, -1, 0);
	if (result == MAP_FAILED) {
		result = "mmap: scratch2";
		goto fail;
	}

	result = mmap(NULL, 8192, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
	if (result == MAP_FAILED) {
		result = "mmap: trampoline";
		goto fail;
	}
	trampoline = result;
	void *trampoline_finish = ((void*)&test_case_exit - (void*)&routine_begin) + trampoline;
	memcpy(code_data + sizeof(code_data) - 8, &trampoline_finish, 8);

	result = mmap(NULL, 8192, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
	if (result == MAP_FAILED) {
		result = "mmap: code";
		goto fail;
	}
	code = result;

	memcpy(trampoline, routine_begin, (void *)&routine_end - (void *)&routine_begin);
	memcpy(code, code_data, sizeof(code_data));

	return;

fail:
	perror(result);
	exit(EXIT_FAILURE);
}

void print_zmm_buffer(uint8_t *buffer) {
	for (int i = 0; i < 64; i++) {
		printf("%02x", buffer[i]);
	}
	puts("");
}

int main() {
	init();

	for (int r = 0;; r++) {
		struct execution_result result = { 0 };
		uint8_t initial_zmm19[64] = { 0 };
		uint8_t initial_zmm24[64] = { 0 };

		memcpy(&result.state.zmm[19], "\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f\x10", 16);
		do_test(trampoline, code, sizeof(code_data), &result);
		if (result.faulted)
			printf("faulted: %d\n", result.faulted);

		printf(
		"attempt %d results:\n"
		"\tinitial zmm19: " HEXFMT64 "\n"
		"\tfinal zmm19:   " HEXFMT64 "\n"
		"\tinitial zmm24: " HEXFMT64 "\n"
		"\tfinal zmm24:   " HEXFMT64 "\n",
		r, HEXARGS64(initial_zmm19, 0), HEXARGS64(result.state.zmm[19], 0), HEXARGS64(initial_zmm24, 0), HEXARGS64(result.state.zmm[24], 0)
		);

		bool all_zeros = true;
		for (int i = 0; i < 64; i++) {
			all_zeros &= result.state.zmm[24][i] == 0;
		}

		if (all_zeros) {
			printf("ATTEMPT SUCCEEDED on iteration %d: zmm24 is all zeros, even though it shouldn't be....\n", r);
			break;
		}
	}

	return 0;
}