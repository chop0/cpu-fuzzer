#define _GNU_SOURCE
#include <sched.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <immintrin.h>
#include <pthread.h>

#define HEXFMT8 "%02hhx%02hhx%02hhx%02hhx%02hhx%02hhx%02hhx%02hhx"
#define HEXFMT16  HEXFMT8 HEXFMT8

#define XMMARGS(x, offset) (((uint8_t*)&x))[(offset)]
#define HEXARGS8(x, offset) XMMARGS(x, (offset)), XMMARGS(x, (offset)+1), XMMARGS(x, (offset)+2), XMMARGS(x, (offset)+3), XMMARGS(x, (offset)+4), XMMARGS(x, (offset)+5), XMMARGS(x, (offset)+6), XMMARGS(x, (offset)+7)
#define HEXARGS16(x, offset) HEXARGS8(x, (offset)), HEXARGS8(x, (offset)+8)

static int spam_thread_id = 0;
static uint8_t big_buffer[8192] = { 0 };

static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t cond = PTHREAD_COND_INITIALIZER;

static uint8_t *scratch1 = big_buffer, *scratch2 = big_buffer + 4096;

#define TEST_INSTRUCTIONS(V) \
	V("vmovups (%0), %%xmm17") \
	V("vpxord %%xmm24, %%xmm24, %%xmm24") \
	\
	V("vmovlps (%1), %%xmm17, %%xmm24") \
	V("vmovhpd (%2), %%xmm0, %%xmm8") \
	\
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
	__m128 result = _mm_loadl_pi(input, scratch1);

	return result;
}

static void random_bytes(uint8_t *buf, size_t len) {
	while (len >= 4) {
		*((uint32_t*) (buf)) = rand();
		buf += 4;
		len -= 4;
	}

	while (len--) {
		*buf++ = rand();
	}
}

static __m128 random_m128() {
	__m128 result;
	random_bytes((uint8_t*) &result, sizeof(result));
	return result;
}

static __m512 random_m512() {
	__m512 result;
	random_bytes((uint8_t*) &result, sizeof(result));
	return result;
}

static bool m128_eq(__m128 a, __m128 b) {
	__m128 vcmp = _mm_cmpneq_ps(a, b);
	uint16_t test = _mm_movemask_epi8((__m128i) vcmp);
	return test == 0;
}

static inline void spam_vector_registers(void) {
	__m512 value = random_m512();

	#define VALUES(V) V(0) V(1) V(2) V(3) V(4) V(5) V(6) V(7) V(8) V(9) V(10) V(11) V(12) V(13) V(14) V(15) V(16) V(17) V(18) V(19) V(20) V(21) V(22) V(23) V(24) V(25) V(26) V(27) V(28) V(29) V(30) V(31)

	switch (rand() & 0x1F) {
		#define CASE(x) case x: asm volatile ("vmovups %0, %%zmm" #x : : "m"(value)); break;
		VALUES(CASE)
	}
}

static void spam_register_thread(void) {
	pthread_mutex_lock(&mutex);
	spam_thread_id = gettid();
	pthread_cond_signal(&cond);
	pthread_mutex_unlock(&mutex);

	for (;;) {
		spam_vector_registers();
	}
}

static void pin_to_core(int pid, int core) {
	cpu_set_t set;
	CPU_ZERO(&set);
	CPU_SET(core, &set);
	if (sched_setaffinity(pid, sizeof(set), &set) == -1) {
		perror("sched_setaffinity");
		exit(1);
	}
}

static int setup_spam_thread(pthread_t *thread) {
	if (pthread_mutex_lock(&mutex) != 0) {
		perror("pthread_mutex_lock");
		exit(1);
	}

	if (pthread_create(thread, NULL, (void*(*)(void*)) spam_register_thread, NULL) != 0) {
		perror("pthread_create");
		exit(1);
	}
	while (!spam_thread_id) pthread_cond_wait(&cond, &mutex);
	pthread_mutex_unlock(&mutex);
	pthread_detach(*thread);

	return spam_thread_id;
}

int main() {
	__m128 input = random_m128();
//
//	memset(scratch2, 0x55555555, 64);
//	memset(scratch1, 0x44444444, 64);
//	pthread_t spam_thread;
//
//	int my_tid = gettid();
//	int spam_tid = setup_spam_thread(&spam_thread);
//
//	pin_to_core(my_tid, 0);
//	pin_to_core(spam_tid, 0); // same core as the spam thread

	printf("%hhx%hhx\n", *scratch1, *scratch2);

	srand(0);

	for (int r = 0; ; r++) {
		__m128 intrinsics_result = reproducer_intrinsics(input);
		__m128 serialized_result = reproducer_poc(true, input);

		for (int i = 0; i < 1024; i++) {
			spam_vector_registers();
		}
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