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
#define HEXFMT64  HEXFMT32 HEXFMT32

#define HEXARGS8(x, offset) (x)[offset], (x)[offset+1], (x)[offset+2], (x)[offset+3], (x)[offset+4], (x)[offset+5], (x)[offset+6], (x)[offset+7]
#define HEXARGS32(x, offset) HEXARGS8(x, offset), HEXARGS8(x, offset+8), HEXARGS8(x, offset+16), HEXARGS8(x, offset+24)
#define HEXARGS64(x, offset) HEXARGS32(x, offset), HEXARGS32(x, offset+32)

static void reproducer_code(uint8_t const *initial_zmm19, uint8_t const *initial_zmm24, uint8_t *final_zmm19, uint8_t *final_zmm24) {
	asm (
		"vmovups (%0), %%zmm19\n"
		"vmovups (%1), %%zmm24\n"
		: : "r"(initial_zmm19), "r"(initial_zmm24)
		: "zmm19", "zmm24"
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
		"vmovups %%zmm19, (%0)\n"
		"vmovups %%zmm24, (%1)\n"
		: : "r"(final_zmm19), "r"(final_zmm24)
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
		uint8_t initial_zmm19[64] = { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0 };
		uint8_t initial_zmm24[64] = { 0 };

		uint8_t final_zmm19[64];
		uint8_t final_zmm24[64];

		memcpy(&final_zmm19, initial_zmm19, 64);
		memcpy(&final_zmm24, initial_zmm24, 64);

		reproducer_code(initial_zmm19, initial_zmm24, final_zmm19, final_zmm24);

		printf(
			"attempt %d results:\n"
			"\tinitial zmm19: " HEXFMT64 "\n"
			"\tfinal zmm19:   " HEXFMT64 "\n"
			"\tinitial zmm24: " HEXFMT64 "\n"
			"\tfinal zmm24:   " HEXFMT64 "\n",
		r, HEXARGS64(initial_zmm19, 0), HEXARGS64(final_zmm19, 0), HEXARGS64(initial_zmm24, 0), HEXARGS64(final_zmm24, 0)
		);

		uint8_t zeros[64] = { 0 };
		bool all_zeros = memcmp(final_zmm24, zeros, 64) == 0;

		found_all_zeros |= all_zeros;
		found_non_zero |= !all_zeros;

		if (found_all_zeros && found_non_zero) {
			printf("SUCCESS: found 1 attempt where zmm24 was all zeros and 1 attempt where it was not\n");
			return 0;
		}
	}
	fprintf(stderr, "ALL ATTEMPTS FAILED\n");
	return 1;
}