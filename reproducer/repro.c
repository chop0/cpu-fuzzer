#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <immintrin.h>

#define HEXFMT8 "%02hhx%02hhx%02hhx%02hhx%02hhx%02hhx%02hhx%02hhx"
#define HEXFMT16  HEXFMT8 HEXFMT8

#define XMMARGS(x, offset) (((uint8_t*)&x))[(offset)]
#define HEXARGS8(x, offset) XMMARGS(x, (offset)), XMMARGS(x, (offset)+1), XMMARGS(x, (offset)+2), XMMARGS(x, (offset)+3), XMMARGS(x, (offset)+4), XMMARGS(x, (offset)+5), XMMARGS(x, (offset)+6), XMMARGS(x, (offset)+7)
#define HEXARGS16(x, offset) HEXARGS8(x, (offset)), HEXARGS8(x, (offset)+8)

static uint8_t big_buffer[8192] = { 0 };

static uint8_t *scratch1 = big_buffer, *scratch2 = big_buffer + 4096;

#define TEST_INSTRUCTIONS(V) \
	V("vmovups (%0), %%xmm16") \
	V("vpxord %%xmm24, %%xmm24, %%xmm24") \
	V("or %%r11, %%r11") \
	V("vhsubpd (%1), %%xmm0, %%xmm0") \
	V("cvtps2dq (%2), %%xmm0") \
	V("vrsqrtps (%2), %%xmm0") \
	V("vfmsub231ss (%2), %%xmm0, %%xmm0") \
	V("vmovlps (%2), %%xmm16, %%xmm24") \
	V("vmovhpd (%2), %%xmm0, %%xmm8") \
	V("vmovups %%xmm24, (%3)")

static __m128 reproducer_poc(bool with_fences, __m128 input) {
	uint8_t output[64] = { 0 };
	if (with_fences) {
		#define FENCED(x) x "\nmfence\n"
		asm (
			TEST_INSTRUCTIONS(FENCED)
			: : "r"(&input), "r"(scratch1), "r"(scratch2), "r"(&output)
			: "xmm0", "xmm8", "xmm16", "xmm24", "memory"
		);
		#undef FENCED
	} else {
		#define UNFENCED(x) x "\n"
		asm (
			TEST_INSTRUCTIONS(UNFENCED)
			: : "r"(&input), "r"(scratch1), "r"(scratch2), "r"(&output)
			: "xmm0", "xmm8", "xmm16", "xmm24", "memory"
		);
		#undef UNFENCED
	}

	return *((__m128*) output);
}

static __m128 reproducer_intrinsics(__m128 input) {
	__m64 zero = { 0 };
	__m128 result = _mm_loadl_pi(input, &zero);

	return result;
}

static __m128 random_m128() {
	uint32_t buf[4] __attribute__((aligned(16)));
	for (int i = 0; i < 4; i++) {
		buf[i] = rand();
	}

	return *((__m128*) buf);
}

static bool m128_eq(__m128 a, __m128 b) {
	__m128 vcmp = _mm_cmpneq_ps(a, b);
	uint16_t test = _mm_movemask_epi8((__m128i) vcmp);
	return test == 0;
}

int main() {
	printf("%hhx%hhx\n", *scratch1, *scratch2);

	srand(0);

	for (int r = 0; r < 100; r++) {
		__m128 input = random_m128();
		__m128 intrinsics_result = reproducer_intrinsics(input);
		__m128 serialized_result = reproducer_poc(true, input);
		__m128 broken_result = reproducer_poc(false, input);

		printf(
			"attempt %d results:\n"
			"\tinput to function:\t" HEXFMT16 "\n"
			"\tintrinsics result:\t" HEXFMT16 "\n"
			"\tpoc w/ fences result:\t" HEXFMT16 "\n"
			"\tpoc w/o fences result:\t" HEXFMT16 "\n",
		r, HEXARGS16(input, 0), HEXARGS16(intrinsics_result, 0), HEXARGS16(serialized_result, 0), HEXARGS16(broken_result, 0)
		);

		if (!m128_eq(broken_result, intrinsics_result)) {
			printf("attempt %d SUCCESS: found an attempt where xmm24 was NOT what we expected\n", r);
			return 0;
		} else {
			printf("attempt %d FAILURE: xmm24 was what we expected;  trying again.\n", r);
		}
	}
	fprintf(stderr, "ALL ATTEMPTS FAILED\n");
	return 1;
}