package ax.xz.xed;

import static ax.xz.xed.xed_4.*;

public record OperandAction(boolean read, boolean write, boolean readConditional, boolean writeCondition) {
	public static OperandAction from(int accessEnum) {
		return new OperandAction(
			xed_operand_action_read(accessEnum) != 0,
			xed_operand_action_written(accessEnum) != 0,
			xed_operand_action_conditional_read(accessEnum) != 0,
			xed_operand_action_conditional_write(accessEnum) != 0
		);
	}
}
