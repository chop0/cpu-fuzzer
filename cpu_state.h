#ifndef CPU_STATE_H
#define CPU_STATE_H

#include <stdint.h>

#define BIT(n) (1 << (n))

#define XSAVE_COMPONENT_X87 				(0)
#define XSAVE_COMPONENT_SSE 				(1)
#define XSAVE_COMPONENT_AVX 				(2)
#define XSAVE_COMPONENT_BNDREGS 			(3)
#define XSAVE_COMPONENT_BNDCSR 			(4)
#define XSAVE_COMPONENT_AVX_OPMASK 		(5)
#define XSAVE_COMPONENT_AVX_ZMM_Hi256 	(6)
#define XSAVE_COMPONENT_AVX_Hi16_ZMM 		(7)

typedef struct {
	uint8_t fpu[160];
	uint8_t xmm[16][16];
	uint8_t reserved[48];
	uint8_t unused[48];
} xsave_legacy_region_t;
_Static_assert(sizeof(xsave_legacy_region_t) == 512, "xsave_legacy_region_t size is not 512 bytes");

typedef struct {
	uint64_t xstate_bv;
	uint64_t xcomp_bv;
	uint8_t reserved[48];
} xsave_header_t;
_Static_assert(sizeof(xsave_header_t) == 64, "xsave_header_t size is not 64 bytes");

typedef struct {
	xsave_legacy_region_t legacy_region;
	xsave_header_t header;
	uint8_t data[];
} xsave_region_t;

size_t get_xsave_size(void);
size_t get_xsave_offset(uint64_t feature);
size_t get_xsave_feature_size(uint64_t feature);

#endif