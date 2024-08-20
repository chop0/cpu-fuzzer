package ax.xz.fuzz.x86.arch;

public enum x86RegisterBank {
	GPRQ, GPRD, GPRW, GPRL, GPRH,
	LOWER_XMM, UPPER_XMM,
	LOWER_YMM, UPPER_YMM,
	LOWER_ZMM, UPPER_ZMM,
	MMX, CR, TMM, MASK, SEGMENT,
	SPECIAL
}
