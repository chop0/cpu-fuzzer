package ax.xz.xed;

import static ax.xz.xed.xed_15.xed_iclass_enum_t2str;
import static ax.xz.xed.xed_5.XED_ICLASS_LAST;

public record InstructionIclass(int xedEnum, String name) {
	private static final InstructionIclass[] values = load();

	private static InstructionIclass[] load() {
		int last = XED_ICLASS_LAST();
		var result = new InstructionIclass[last - 1];

		for (int i = 0; i < result.length; i++) {
			result[i] = new InstructionIclass(i + 1, xed_iclass_enum_t2str(i + 1).getString(0));
		}

		return result;
	}

	public static InstructionIclass from(int xedEnum) {
		if (xedEnum == 0) {
			throw new IllegalArgumentException("xedEnum is 0");
		}

		var result = values[xedEnum - 1];
		if (result.xedEnum != xedEnum) {
			throw new AssertionError("mismatched enum: " + xedEnum + ", " + result);
		}

		return result;
	}

	@Override
	public String toString() {
		return name;
	}
}
