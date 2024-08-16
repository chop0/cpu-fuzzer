package ax.xz.xed;

import java.util.Optional;

import static ax.xz.xed.xed_15.*;

public enum OperandVisibility {
	EXPLICIT(XED_OPVIS_EXPLICIT()),
	IMPLICIT(XED_OPVIS_IMPLICIT()),
	SUPPRESSED(XED_OPVIS_SUPPRESSED()),
	LAST(XED_OPVIS_LAST());

	private final int xedEnum;

	OperandVisibility(int xedEnum) {
		this.xedEnum = xedEnum;
	}

	public static Optional<OperandVisibility> from(int xedEnum) {
		if (xedEnum == 0) {
			return Optional.empty();
		}

		var result = values()[xedEnum - 1];
		if (result.xedEnum != xedEnum) {
			throw new AssertionError("mismatched enum: " + xedEnum + ", " + result);
		}

		return Optional.of(result);
	}
}
