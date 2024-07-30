.globl _start
.intel_syntax noprefix

.section .data

zeros: .quad 0, 0, 0, 0, 0, 0, 0, 0

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
	push rbx
	push rbp

	xor eax, eax

	jmp after_align

.align 4096
	after_align:
	push 515
	popfq

	vmovups zmm0, [rip + zeros]
	vmovups zmm1, [rip + zeros]
	vmovups zmm2, [rip + zeros]
	vmovups zmm3, [rip + zeros]
	vmovups zmm4, [rip + zeros]
	vmovups zmm5, [rip + zeros]
	vmovups zmm6, [rip + zeros]
	vmovups zmm7, [rip + zeros]
	vmovups zmm8, [rip + zeros]
	vmovups zmm9, [rip + zeros]
	vmovups zmm10, [rip + zeros]
	vmovups zmm11, [rip + zeros]
	vmovups zmm12, [rip + zeros]
	vmovups zmm13, [rip + zeros]
	vmovups zmm14, [rip + zeros]
	vmovups zmm15, [rip + zeros]
	vmovups zmm16, [rip + zeros]
	vmovups zmm17, [rip + zeros]
	vmovups zmm18, [rip + zeros]
	vmovups zmm19, [rip + zeros]
	vmovups zmm20, [rip + zeros]
	vmovups zmm21, [rip + zeros]
	vmovups zmm22, [rip + zeros]
	vmovups zmm23, [rip + zeros]
	vmovups zmm24, [rip + zeros]
	vmovups zmm25, [rip + zeros]
	vmovups zmm26, [rip + zeros]
	vmovups zmm27, [rip + zeros]
	vmovups zmm28, [rip + zeros]
	vmovups zmm29, [rip + zeros]
	vmovups zmm30, [rip + zeros]
	vmovups zmm31, [rip + zeros]

	vmovaps xmm0,xmm1
	vcmppd ymm0,ymm14,ymm8,0x4a
	vmulsd xmm11,xmm11,xmm0

	vmovups [rdi], xmm11

	pop rbp
	pop rbx
	ret

_start:
	sub rsp, 16
	mov rcx, 32

loop:
	lea rdi, [rsp]
	call demo
	call demo
	call demo

	callq demo

	leaq rdi, [rsp]
	mov rsi, r8
	movq rdx, r9
	callq print_result

	movq rax, rdi
	callq exit
