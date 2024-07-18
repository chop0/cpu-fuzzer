#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>
#include <sys/mman.h>
#include <assert.h>
#include <stdbool.h>

#define HEXFMT8 "%02x%02x%02x%02x%02x%02x%02x%02x"
#define HEXFMT32  HEXFMT8 HEXFMT8 HEXFMT8 HEXFMT8

#define HEXARGS8(x, offset) (x)[offset], (x)[offset+1], (x)[offset+2], (x)[offset+3], (x)[offset+4], (x)[offset+5], (x)[offset+6], (x)[offset+7]
#define HEXARGS32(x, offset) HEXARGS8(x, offset), HEXARGS8(x, offset+8), HEXARGS8(x, offset+16), HEXARGS8(x, offset+24)

static void reproducer_code(uint8_t const *initial_xmm19, uint8_t const *initial_xmm24, uint8_t *final_xmm19, uint8_t *final_xmm24) {
	asm (
		"vmovups (%0), %%xmm19\n"
		"vmovups (%1), %%xmm24\n"
		: : "r"(initial_xmm19), "r"(initial_xmm24)
		: "xmm19", "xmm24"
	);

	asm (
		"or %%r11, %%r11\n"
		"vhsubpd (0x210000), %%xmm12, %%xmm12\n"
		"cvtps2dq (0x110000), %%xmm10\n"
		"vrsqrtps (0x110000), %%xmm4\n"
		"vfmsub231ss (0x110000), %%xmm4, %%xmm0\n"
		"vmovlps (0x110000), %%xmm19, %%xmm24\n"
		"vmovhpd (0x110000), %%xmm0, %%xmm8\n"
		: : : "xmm0", "xmm4", "xmm8", "xmm10", "xmm12", "xmm19", "xmm24"
	);

	asm (
		"vmovups %%xmm19, (%0)\n"
		"vmovups %%xmm24, (%1)\n"
		: : "r"(final_xmm19), "r"(final_xmm24)
		: "memory"
	);
}

static void *mmap_rwx(void *address, char const* name) {
	void *result = mmap(address, 8192, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS | (address ? MAP_FIXED : 0), -1, 0);
	if (result == MAP_FAILED) {
		printf("mmap: ");
		perror("name");
		exit(EXIT_FAILURE);
	}

	assert(address == NULL || result == address);

	return result;
}

static void init() {
	mmap_rwx((void *)0x110000, "scratch1");
	mmap_rwx((void *)0x210000, "scratch2");
}

int main() {
	init();

	bool found_all_zeros = false;
	bool found_non_zero = false;

	for (int r = 0; r < 100; r++) {
		uint8_t initial_xmm19[32] = { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0 };
		uint8_t initial_xmm24[32] = { 0 };

		uint8_t final_xmm19[32];
		uint8_t final_xmm24[32];

		memcpy(&final_xmm19, initial_xmm19, 32);
		memcpy(&final_xmm24, initial_xmm24, 32);

		reproducer_code(initial_xmm19, initial_xmm24, final_xmm19, final_xmm24);

		printf(
			"attempt %d results:\n"
			"\tinitial xmm19: " HEXFMT32 "\n"
			"\tfinal xmm19:   " HEXFMT32 "\n"
			"\tinitial xmm24: " HEXFMT32 "\n"
			"\tfinal xmm24:   " HEXFMT32 "\n",
		r, HEXARGS32(initial_xmm19, 0), HEXARGS32(final_xmm19, 0), HEXARGS32(initial_xmm24, 0), HEXARGS32(final_xmm24, 0)
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