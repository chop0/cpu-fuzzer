.globl _start
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

	cpuid # serialise
	jmp after_align

.align 4096
	after_align:

	vmovlps mem_1(%rip), %xmm17, %xmm24
	# mfence
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
	callq demo # warm up to prevent mispredictions/DSB miss/other weirdness

	vmovups input(%rip), %xmm17
	vmovups mem_1(%rip), %xmm0
	vmovups mem_1(%rip), %xmm8

	callq demo

	leaq 0(%rsp), %rdi
	callq print_result

	movq %rax, %rdi
	callq exit
