package ax.xz.fuzz.instruction;

import java.util.EnumSet;

public enum StatusFlag {
	CF, PF, AF, ZF, SF, TF, IF, DF, OF, IOPL, NT, MD, RF, VM, AC, VIF, VIP, ID, AI, UIF,
	FC0, FC1, FC2, FC3;

	public static EnumSet<StatusFlag> all() {
		return EnumSet.allOf(StatusFlag.class);
	}
}
