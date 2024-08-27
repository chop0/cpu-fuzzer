.altmacro

.macro iterate op, start, end
    \op \start
    .if \end-\start
        iterate op, %(\start+1), \end
    .endif
.endm


.macro iter_op_inner mnemonic, a, b, c
	\mnemonic \a, \b(\c)
.endm

.macro registers_op mnemonic, bank, start, end, base, scale, displacement
	.macro iter_op p
		iter_op_inner \mnemonic, \bank\p, %((\p-\start)*\scale+\displacement), \base
	.endm
	iterate iter_op, \start, \end
	.purgem iter_op
.endm
