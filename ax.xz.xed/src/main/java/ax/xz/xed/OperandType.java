package ax.xz.xed;

import java.util.Optional;

import static ax.xz.xed.xed_15.*;

public enum OperandType {
	ERROR(XED_OPERAND_TYPE_ERROR()),
	IMM(XED_OPERAND_TYPE_IMM()),
	IMM_CONST(XED_OPERAND_TYPE_IMM_CONST()),
	NT_LOOKUP_FN(XED_OPERAND_TYPE_NT_LOOKUP_FN()),
	NT_LOOKUP_FN2(XED_OPERAND_TYPE_NT_LOOKUP_FN2()),
	NT_LOOKUP_FN4(XED_OPERAND_TYPE_NT_LOOKUP_FN4()),
	REG(XED_OPERAND_TYPE_REG()),
	LAST(XED_OPERAND_TYPE_LAST());

	private final int xedEnum;

	OperandType(int xedEnum) {
		this.xedEnum = xedEnum;
	}

	public static Optional<OperandType> from(int xedEnum) {
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
