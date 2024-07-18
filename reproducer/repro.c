#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <immintrin.h>

#define HEXFMT8 "%02hhx%02hhx%02hhx%02hhx%02hhx%02hhx%02hhx%02hhx"
#define HEXFMT32  HEXFMT8 HEXFMT8 HEXFMT8 HEXFMT8

#define XMMARGS(x, offset) (((uint8_t*)&x))[(offset)]
#define HEXARGS8(x, offset) XMMARGS(x, (offset)), XMMARGS(x, (offset)+1), XMMARGS(x, (offset)+2), XMMARGS(x, (offset)+3), XMMARGS(x, (offset)+4), XMMARGS(x, (offset)+5), XMMARGS(x, (offset)+6), XMMARGS(x, (offset)+7)
#define HEXARGS32(x, offset) HEXARGS8(x, (offset)), HEXARGS8(x, (offset)+8), HEXARGS8(x, (offset)+16), HEXARGS8(x, (offset)+24)

static uint8_t big_buffer[8192] = { 0 };

static uint8_t *scratch1 = big_buffer, *scratch2 = big_buffer + 4096;

#define TEST_INSTRUCTIONS(V) \
	V("or %%r11, %%r11") \
	V("vhsubpd (%0), %%xmm0, %%xmm0") \
	V("cvtps2dq (%1), %%xmm0") \
	V("vrsqrtps (%1), %%xmm0") \
	V("vfmsub231ss (%1), %%xmm0, %%xmm0") \
	V("vmovlps (%1), %%xmm16, %%xmm24") \
	V("vmovhpd (%1), %%xmm0, %%xmm8")

static __m128 reproducer_poc(bool with_fences, __m128 input) {
	asm (
		"vmovups %0, %%xmm16\n"
		"vpxord %%xmm24, %%xmm24, %%xmm24\n"
		: : "m"(input)
		: "xmm16", "xmm24"
	);

	if (with_fences) {
		#define FENCED(x) x "\nmfence\n"
		asm (
			TEST_INSTRUCTIONS(FENCED)
			: : "r"(scratch1), "r"(scratch2)
			: "xmm0", "xmm8", "xmm16", "xmm24"
		);
		#undef FENCED
	} else {
		#define UNFENCED(x) x "\n"
		asm (
			TEST_INSTRUCTIONS(UNFENCED)
			: : "r"(scratch1), "r"(scratch2)
			: "xmm0", "xmm8", "xmm16", "xmm24"
		);
		#undef UNFENCED
	}

	__m128 output;
	asm (
		"vmovups %%xmm24, (%0)\n"
		: : "r"(&output)
		: "memory"
	);

	return output;
}

static __m128 reproducer_intrinsics(__m128 input) {
	__m128 zero = { 0 };
	__m128 result = _mm_loadl_pi(input, &zero);

	return result;
}

int main() {
	printf("%hhx%hhx\n", *scratch1, *scratch2);
	srand(0);

	uint8_t test_input[32];
	for (int i = 0; i < sizeof(test_input); i++) {
		test_input[i] = rand() & 0xff;
	}
	__m128 input = *((__m128*) test_input);

	for (int r = 0; r < 100; r++) {
		__m128 intrinsics_result = reproducer_intrinsics(input);
		__m128 serialized_result = reproducer_poc(true, input);
		__m128 broken_result = reproducer_poc(false, input);

		printf(
			"attempt %d results:\n"
			"\tinput to function:\t" HEXFMT32 "\n"
			"\tintrinsics result:\t" HEXFMT32 "\n"
			"\tpoc w/ fences result:\t" HEXFMT32 "\n"
			"\tpoc w/o fences result:\t" HEXFMT32 "\n",
		r, HEXARGS32(test_input, 0), HEXARGS32(intrinsics_result, 0), HEXARGS32(serialized_result, 0), HEXARGS32(broken_result, 0)
		);

		bool correct_result = memcmp(&broken_result, &intrinsics_result, 32) == 0;
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