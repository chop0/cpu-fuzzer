#include <stdint.h>
#include <stdio.h>
#include <string.h>

#define HEXFMT8 "%02hhx%02hhx%02hhx%02hhx%02hhx%02hhx%02hhx%02hhx"
#define HEXFMT16  HEXFMT8 HEXFMT8

#define XMMARGS(x, offset) (((uint8_t const*)x))[(offset)]
#define HEXARGS8(x, offset) XMMARGS(x, (offset)), XMMARGS(x, (offset)+1), XMMARGS(x, (offset)+2), XMMARGS(x, (offset)+3), XMMARGS(x, (offset)+4), XMMARGS(x, (offset)+5), XMMARGS(x, (offset)+6), XMMARGS(x, (offset)+7)
#define HEXARGS16(x, offset) HEXARGS8(x, (offset)), HEXARGS8(x, (offset)+8)

int print_result( uint8_t const* a) {
	printf(
		"--- attempt results --- \n"
		"\tresult a (no mfence): " HEXFMT16 "\n",
	 HEXARGS16(a, 0)
	);

	return a[8] != 0;
}
