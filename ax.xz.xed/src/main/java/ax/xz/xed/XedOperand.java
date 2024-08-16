package ax.xz.xed;

import java.lang.foreign.MemorySegment;
import java.util.Optional;
import java.util.OptionalInt;

import static ax.xz.xed.OperandType.IMM_CONST;
import static ax.xz.xed.OperandType.REG;
import static ax.xz.xed.xed_13.xed_operand_enum_t2str;
import static ax.xz.xed.xed_4.xed_operand_width_bits;

public record XedOperand(String name,
			 OperandVisibility visibility,
			 OperandAction action,
			 OperandWidth width,
			 OperandType type,

			 Optional<OperandXtype> xtype,
			 Optional<OperandNonterminal> nonterminal,
			 Optional<XedRegister> implicitReg,
			 OptionalInt implicitConstant
) {

	public static XedOperand from(MemorySegment xedOperand) {
		var operandEnum = xed_operand_t._name(xedOperand);
		var name = xed_operand_enum_t2str(operandEnum).getString(0);

		var visibility = OperandVisibility.from(xed_operand_t._operand_visibility(xedOperand)).orElseThrow();
		var action = OperandAction.from(xed_operand_t._rw(xedOperand));
		var width = new OperandWidth(
			xed_operand_width_bits(xedOperand, 1),
			xed_operand_width_bits(xedOperand, 2),
			xed_operand_width_bits(xedOperand, 3)
		);
		var type = OperandType.from(xed_operand_t._type(xedOperand)).orElseThrow();
		var xtype = OperandXtype.from(xed_operand_t._xtype(xedOperand));
		var nonterminal = getNonterminal(xedOperand);
		var implicitReg = getImplicitRegister(xedOperand, visibility, type);
		var implicitConstant = getImplicitConstant(xedOperand, type);


		return new XedOperand(name, visibility, action, width, type, xtype, nonterminal, implicitReg, implicitConstant);
	}

	private static Optional<OperandNonterminal> getNonterminal(MemorySegment xedOperand) {
		if (xed_operand_t._nt(xedOperand) != 0)
			return Optional.of(OperandNonterminal.from(xed_operand_t._u._nt(xed_operand_t._u(xedOperand))));
		return Optional.empty();
	}

	private static Optional<XedRegister> getImplicitRegister(MemorySegment xedOperand, OperandVisibility visibility, OperandType type) {
		if ((visibility == OperandVisibility.IMPLICIT && type == REG)) {
			int xedEnum = xed_operand_t._u._reg(xed_operand_t._u(xedOperand));
			return XedRegister.from(xedEnum);
		} else {
			return Optional.empty();
		}
	}

	private static OptionalInt getImplicitConstant(MemorySegment xedOperand, OperandType type) {
		if (type == IMM_CONST) {
			return OptionalInt.of(xed_operand_t._u._imm(xed_operand_t._u(xedOperand)));
		} else {
			return OptionalInt.empty();
		}
	}

}
