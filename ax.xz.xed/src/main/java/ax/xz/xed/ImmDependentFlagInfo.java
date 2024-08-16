package ax.xz.xed;

import java.lang.foreign.MemorySegment;

import static ax.xz.xed.xed_internal_1.*;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public record ImmDependentFlagInfo(SimpleFlagInfo withImm0, SimpleFlagInfo withImm1, SimpleFlagInfo withImmOther) implements FlagInfo {
	public static ImmDependentFlagInfo fromComplexFlag(MemorySegment p) {
		if ((p.get(JAVA_BYTE, 0) & 2) == 0) {
			throw new IllegalArgumentException("Complex flags does not have checkImm bit set");
		}

		return new ImmDependentFlagInfo(
			SimpleFlagInfo.fromSimpleFlag(xed_flags_simple_table(xed_complex_flag_t.cases(p, XED_FLAG_CASE_IMMED_ZERO()))),
			SimpleFlagInfo.fromSimpleFlag(xed_flags_simple_table(xed_complex_flag_t.cases(p, XED_FLAG_CASE_IMMED_ONE()))),
			SimpleFlagInfo.fromSimpleFlag(xed_flags_simple_table(xed_complex_flag_t.cases(p, XED_FLAG_CASE_IMMED_OTHER())))
		);
	}
}
