package ax.xz.xed;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;

import static ax.xz.xed.xed.*;

public record CpuidGroup(String name, List<CpuidRecord> records) {
	public static CpuidGroup from(int xedEnum) {
		var records = new ArrayList<CpuidRecord>();

		try (var arena = Arena.ofConfined()) {
			for (int j = 0; j < XED_MAX_CPUID_RECS_PER_GROUP(); j++) {
				int cpuidrec;
				var crec = xed_cpuid_rec_t.allocate(arena);
				boolean r;
				cpuidrec = xed_get_cpuid_rec_enum_for_group(xedEnum, j);
				if (cpuidrec == XED_CPUID_REC_INVALID()) {
					break;
				}

				r = xed_get_cpuid_rec(cpuidrec, crec) != 0;
				if (r) {
					records.add(CpuidRecord.from(crec));
				} else {
					throw new IllegalArgumentException("Could not find cpuid leaf information\n");
				}
			}
		}

		return new CpuidGroup(xed_cpuid_group_enum_t2str(xedEnum).getString(0), records);
	}

}
