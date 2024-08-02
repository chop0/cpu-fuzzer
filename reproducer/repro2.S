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

	movq rbx, 0x00000000fffff9d0
		sbb    ebx,0x67
	movsldup xmm9, [rip + mem_1]
	vaesenclast xmm4, xmm12, [rip + mem_2]

	movq [rdi], rbx

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
	mov rsi, 8
	callq print_result

	movq rax, rdi
	callq exit
