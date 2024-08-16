package ax.xz.xed;

import static ax.xz.xed.xed_5.*;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;


public record InstructionIform(int xedEnum,
			       String name,
			       InstructionIclass iclass,
			       IformCategory category,
			       IformIsaSet isaSet,
			       IformExtension extension) {
	private static final InstructionIform[] values = load();

	private static InstructionIform[] load() {
		int last = XED_IFORM_LAST();
		var result = new InstructionIform[last - 1];

		for (int i = 0; i < result.length; i++) {
			result[i] = new InstructionIform(i + 1, xed_iform_enum_t2str(i + 1).getString(0),
				InstructionIclass.from(xed_iform_map(i + 1).get(JAVA_SHORT_UNALIGNED, 0) & 0xffff),
				IformCategory.from(xed_iform_to_category(i + 1)).orElseThrow(),
				IformIsaSet.from(xed_iform_to_isa_set(i + 1)),
				IformExtension.from(xed_iform_to_extension(i + 1)).orElseThrow()
				);
		}

		return result;
	}

	public static InstructionIform from(int xedEnum) {
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
