package ax.xz.xed;

import java.lang.foreign.MemorySegment;

import static ax.xz.xed.xed_internal_1.xed_flags_complex_table;
import static ax.xz.xed.xed_internal_1.xed_flags_simple_table;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public sealed interface FlagInfo permits SimpleFlagInfo, ImmDependentFlagInfo, RepDependentFlagInfo {
	static FlagInfo fromInstruction(MemorySegment m) {
		var t_index = xed_inst_s._flag_info_index(m);

		if (t_index == 0)
			return SimpleFlagInfo.empty();
		else if (xed_inst_s._flag_complex(m) == 0)
			return SimpleFlagInfo.fromSimpleFlag(xed_flags_simple_table(t_index));

		var p = xed_flags_complex_table(t_index);

		return switch (p.get(JAVA_BYTE, 0) & 0b11) {
			case 0b01 -> RepDependentFlagInfo.fromComplexFlag(p);
			case 0b10 -> ImmDependentFlagInfo.fromComplexFlag(p);
			default -> throw new AssertionError("Invalid complex flag type");
		};
	}
}
