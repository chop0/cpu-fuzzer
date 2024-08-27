.altmacro

.macro iterate op, start, end
    \op \start
    .if \end-\start
        iterate op, %(\start+1), \end
    .endif
.endm


.macro registers_op mnemonic, bank, start, end, base, scale, displacement
	.macro iter_op_inner mnemonic, a, b, c
        	\mnemonic \a, \b(\c)
        .endm

	.macro iter_op p
		iter_op_inner \mnemonic, \bank\p, %((\p-\start)*\scale+\displacement), \base
	.endm
	iterate iter_op, \start, \end

	.purgem iter_op
	.purgem iter_op_inner
.endm

.macro registers_op_2 mnemonic, bank, start, end, base, scale, displacement, temp
	.macro iter_op_inner mnemonic, a, b, c
        	\mnemonic \a, \b(\c), \temp
        .endm

	.macro iter_op p
		iter_op_inner \mnemonic, \bank\p, %((\p-\start)*\scale+\displacement), \base
	.endm
	iterate iter_op, \start, \end

	.purgem iter_op
	.purgem iter_op_inner
.endm


.global trampoline_begin
.global trampoline_entrypoint
.global trampoline_exitpoint
.global trampoline_end

.macro push_unclobberable_state
	addi sp, sp, -208
	sd gp, 0(sp)
	sd tp, 8(sp)

	registers_op sd, s, 0, 11, sp, 8, 16
	registers_op fsd, fs, 0, 11, sp, 8, 112
.endm

.macro pop_unclobberable_state
	ld gp, 0(sp)
	ld tp, 8(sp)

	registers_op ld, s, 0, 11, sp, 8, 16
        registers_op fld, fs, 0, 11, sp, 8, 112

	addi sp, sp, 208
.endm

.text
trampoline_begin:
saved_rsp:
	.quad 0
addr:
	.quad 0
saved_state:
	.quad 0


trampoline_entrypoint:
	push_unclobberable_state

	sd a0, addr, t0
	sd a1, saved_state, t0
	sd sp, saved_rsp, t0
	mv x31, a1

	registers_op fld, f, 0, 31, x31, 8, 32*8
	registers_op ld, x, 0, 30, x31, 8, 0
	registers_op ld, x, 31, 31, x31, 8, 31*8

	ld t0, addr
	jr t0
trampoline_exitpoint:
	ld x31, saved_state
	sd x1, 8(x31)
	ld x31, saved_state
	registers_op sd, x, 2, 30, x31, 8, 0
	registers_op fsd, f, 0, 31, x31, 8, 32*8

	ld sp, saved_rsp

	pop_unclobberable_state
	ret
trampoline_end: