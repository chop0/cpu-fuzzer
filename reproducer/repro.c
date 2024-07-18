#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>

#define HEXFMT8 "%02x%02x%02x%02x%02x%02x%02x%02x"
#define HEXFMT32  HEXFMT8 HEXFMT8 HEXFMT8 HEXFMT8

#define HEXARGS8(x, offset) (x)[offset], (x)[offset+1], (x)[offset+2], (x)[offset+3], (x)[offset+4], (x)[offset+5], (x)[offset+6], (x)[offset+7]
#define HEXARGS32(x, offset) HEXARGS8(x, offset), HEXARGS8(x, offset+8), HEXARGS8(x, offset+16), HEXARGS8(x, offset+24)

static uint8_t big_buffer[8192] = { 0 };

static void *scratch1 = big_buffer, *scratch2 = big_buffer + 4096;

static void reproducer_code(uint8_t const *initial_xmm16, uint8_t const *initial_xmm24, uint8_t *final_xmm16, uint8_t *final_xmm24) {
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
		"vmovups %%xmm16, (%0)\n"
		"vmovups %%xmm24, (%1)\n"
		: : "r"(final_xmm16), "r"(final_xmm24)
		: "memory"
	);
}

int main() {
	bool found_all_zeros = false;
	bool found_non_zero = false;

	for (int r = 0; r < 100; r++) {
		uint8_t initial_xmm16[32] = { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0 };
		uint8_t initial_xmm24[32] = { 0 };

		uint8_t final_xmm16[32];
		uint8_t final_xmm24[32];

		memcpy(&final_xmm16, initial_xmm16, 32);
		memcpy(&final_xmm24, initial_xmm24, 32);

		reproducer_code(initial_xmm16, initial_xmm24, final_xmm16, final_xmm24);

		printf(
			"attempt %d results:\n"
			"\tinitial xmm16: " HEXFMT32 "\n"
			"\tfinal xmm16:   " HEXFMT32 "\n"
			"\tinitial xmm24: " HEXFMT32 "\n"
			"\tfinal xmm24:   " HEXFMT32 "\n",
		r, HEXARGS32(initial_xmm16, 0), HEXARGS32(final_xmm16, 0), HEXARGS32(initial_xmm24, 0), HEXARGS32(final_xmm24, 0)
		);

		uint8_t zeros[32] = { 0 };
		bool all_zeros = memcmp(final_xmm24, zeros, 32) == 0;

		found_all_zeros |= all_zeros;
		found_non_zero |= !all_zeros;

		if (found_all_zeros && found_non_zero) {
			printf("SUCCESS: found 1 attempt where xmm24 was all zeros and 1 attempt where it was not\n");
			return 0;
		}
	}
	fprintf(stderr, "ALL ATTEMPTS FAILED\n");
	return 1;
}