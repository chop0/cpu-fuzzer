#include "cpu_state.h"
#include <assert.h>

size_t get_xsave_size() {
	size_t result;
	asm ("cpuid" : "=b" (result) : "a" (0x0D), "c" (0x0));
	return result;
}

size_t get_xsave_offset(uint64_t feature) {
	switch (feature) {
		case XSAVE_COMPONENT_X87:
			return 0;
		case XSAVE_COMPONENT_SSE:
			return 160;
		default:
			size_t result;
			asm ("cpuid" : "=b" (result) : "a" (0x0D), "c" (feature));
			return result;
	}
}

size_t get_xsave_feature_size(uint64_t feature) {
	switch (feature) {
		case XSAVE_COMPONENT_X87:
			return 160;
		case XSAVE_COMPONENT_SSE:
			return 256;
		default:
			size_t result;
			asm ("cpuid" : "=a" (result) : "a" (0x0D), "c" (feature));
			return result;
	}
}