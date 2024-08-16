package ax.xz.xed;

import java.lang.foreign.MemorySegment;

import static ax.xz.xed.xed_internal_1.*;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public record RepDependentFlagInfo(SimpleFlagInfo withRep, SimpleFlagInfo withoutRep) implements FlagInfo {
	public static RepDependentFlagInfo fromComplexFlag(MemorySegment p) {
		if ((p.get(JAVA_BYTE, 0) & 1) == 0) {
			throw new IllegalArgumentException("Complex flags does not have checkRep bit set");
		}

		return new RepDependentFlagInfo(
			SimpleFlagInfo.fromSimpleFlag(xed_flags_simple_table(xed_complex_flag_t.cases(p, XED_FLAG_CASE_HAS_REP()))),
			SimpleFlagInfo.fromSimpleFlag(xed_flags_simple_table(xed_complex_flag_t.cases(p, XED_FLAG_CASE_NO_REP())))
		);
	}
}
