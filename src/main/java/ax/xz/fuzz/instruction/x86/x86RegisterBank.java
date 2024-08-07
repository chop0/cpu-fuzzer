package ax.xz.fuzz.instruction.x86;

public enum x86RegisterBank {
	GPRQ, GPRD, GPRW, GPRL, GPRH,
	LOWER_XMM, UPPER_XMM,
	LOWER_YMM, UPPER_YMM,
	LOWER_ZMM, UPPER_ZMM,
	MMX, CR, TMM, MASK, SEGMENT,
	SPECIAL
}
