package ax.xz.xed;

import java.util.ArrayList;
import java.util.List;

import static ax.xz.xed.xed.*;

public record IformIsaSet(int xedEnum, String name, List<CpuidGroup> groups) {
	private static final IformIsaSet[] values = load();

	private static IformIsaSet[] load() {
		int last = XED_IFORM_LAST();
		var result = new IformIsaSet[last - 1];

		for (int i = 0; i < result.length; i++) {
			result[i] = new IformIsaSet(i + 1, xed_isa_set_enum_t2str(i + 1).getString(0),
				loadGroups(i + 1)
			);
		}

		return result;
	}

	private static List<CpuidGroup> loadGroups(int xedEnum) {
		var groups = new ArrayList<CpuidGroup>();

		for (int i = 0; i < XED_MAX_CPUID_GROUPS_PER_ISA_SET(); i++) {
			int cpuidgrp = xed_get_cpuid_group_enum_for_isa_set(xedEnum, i);

			if (cpuidgrp == XED_CPUID_GROUP_INVALID()) {
				break;
			}

			groups.add(CpuidGroup.from(cpuidgrp));
		}

		return groups;
	}

	public static IformIsaSet from(int xedEnum) {
		if (xedEnum == 0) {
			throw new IllegalArgumentException("xedEnum is 0");
		}

		var result = values[xedEnum - 1];
		if (result.xedEnum != xedEnum) {
			throw new AssertionError("mismatched enum: " + xedEnum + ", " + result);
		}

		return result;
	}


}
