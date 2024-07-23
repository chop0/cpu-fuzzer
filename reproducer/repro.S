.globl _start

#ifndef NOP_SIZE
#define NOP_SIZE 3
#endif

#if NOP_SIZE == 1
#define NOP  .byte 0x0f; .byte 0x1f; .byte 0x00
#elif NOP_SIZE == 2
#define NOP .byte 0x66; add %eax, %eax
#elif NOP_SIZE == 3
#define NOP  .byte 0x66; or %r11, %r11
#else
#error "Invalid NOP_SIZE"
#endif

#ifndef NOP_COUNT
#define NOP_COUNT 4
#endif

.section .data

mem_1: .quad 0xdeadbeefdeadbeef
.quad 0
.quad 0
.quad 0
.quad 0
mem_2: .quad 0x4041424344454647

input:
.quad 0x0011223344556677
.quad 0x8899aabbccddeeff

.section .text
demo:
	push %rbx
	push %rbp

	xor %eax, %eax

	cpuid
	jmp after_align

.align 4096
	after_align:

	vmovlps mem_1(%rip), %xmm17, %xmm24
	vmovhps mem_2(%rip), %xmm0, %xmm8


	vmovups %xmm24, (%rdi)

	pop %rbp
	pop %rbx
	ret

_start:
	subq $16, %rsp
	movq $32, %rcx

loop:
	leaq 0(%rsp), %rdi
	callq demo
	callq demo
	callq demo

	vmovups input(%rip), %xmm17
	vmovups mem_1(%rip), %xmm0
	vmovups mem_1(%rip), %xmm8

	callq demo

	leaq 0(%rsp), %rdi
	movq %r8, %rsi
	movq %r9, %rdx
	callq print_result

	movq %rax, %rdi
	callq exit
