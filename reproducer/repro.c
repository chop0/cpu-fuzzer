#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <immintrin.h>

#define HEXFMT8 "%02hhx%02hhx%02hhx%02hhx%02hhx%02hhx%02hhx%02hhx"
#define HEXFMT32  HEXFMT8 HEXFMT8 HEXFMT8 HEXFMT8

#define HEXARGS8(x, offset) (x)[offset], (x)[offset+1], (x)[offset+2], (x)[offset+3], (x)[offset+4], (x)[offset+5], (x)[offset+6], (x)[offset+7]
#define HEXARGS32(x, offset) HEXARGS8(x, offset), HEXARGS8(x, offset+8), HEXARGS8(x, offset+16), HEXARGS8(x, offset+24)

static uint8_t big_buffer[8192] = { 0 };

static void *scratch1 = big_buffer, *scratch2 = big_buffer + 4096;

static void reproducer_code(uint8_t const *initial_xmm16, uint8_t const *initial_xmm24, uint8_t *final_xmm24) {
	asm (
		"vmovups (%0), %%xmm16\n"
		"vmovups (%1), %%xmm24\n"
		: : "r"(initial_xmm16), "r"(initial_xmm24)
		: "xmm16", "xmm24"
	);

	asm (
		"or %%r11, %%r11\n"
		"vhsubpd (%0), %%xmm0, %%xmm0\n"
		"cvtps2dq (%1), %%xmm0\n"
		"vrsqrtps (%1), %%xmm0\n"
		"vfmsub231ss (%1), %%xmm0, %%xmm0\n"
		"vmovlps (%1), %%xmm16, %%xmm24\n"
		"vmovhpd (%1), %%xmm0, %%xmm8\n"
		: : "r"(scratch1), "r"(scratch2)
		: "xmm0", "xmm8", "xmm16", "xmm24"
	);

	asm (
		"vmovups %%xmm24, (%0)\n"
		: : "r"(final_xmm24)
		: "memory"
	);
}

static void reproducer_good(uint8_t const *initial_xmm16, uint8_t const *initial_xmm24, uint8_t *final_xmm24) {
	__m64 zero = { 0 };

	__m128 a = _mm_loadu_ps((float const *)initial_xmm16);
	__m128 result = _mm_loadl_pi(a, &zero);

	_mm_storeu_ps((float *)final_xmm24, result);
}

int main() {
	srand(0);

	uint8_t test_input[64];
	for (int i = 0; i < sizeof(test_input); i++) {
		test_input[i] = rand() & 0xff;
	}

	for (int r = 0; r < 100; r++) {
		uint8_t initial_xmm16[32] = { 0 };
		uint8_t initial_xmm24[32] = { 0 };
		memcpy(initial_xmm16, test_input, 64);

		uint8_t good_result[32], test_result[32];

		reproducer_good(initial_xmm16, initial_xmm24, good_result);
		reproducer_code(initial_xmm16, initial_xmm24, test_result);

		printf(
			"attempt %d results:\n"
			"\tinitial xmm16: " HEXFMT32 "\n"
			"\tinitial xmm24: " HEXFMT32 "\n"
			"\texpected result: " HEXFMT32 "\n"
			"\tactual result:   " HEXFMT32 "\n",
		r, HEXARGS32(initial_xmm16, 0),HEXARGS32(initial_xmm24, 0), HEXARGS32(good_result, 0), HEXARGS32(test_result, 0)
		);

		bool correct_result = memcmp(test_result, good_result, 32) == 0;
		if (!correct_result) {
			printf("attempt %d SUCCESS: found an attempt where xmm24 was NOT what we expected\n", r);
			return 0;
		} else {
			printf("attempt %d FAILURE: xmm24 was what we expected;  trying again.\n", r);
		}
	}
	fprintf(stderr, "ALL ATTEMPTS FAILED\n");
	return 1;
}