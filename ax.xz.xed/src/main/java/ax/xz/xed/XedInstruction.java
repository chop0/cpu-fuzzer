package ax.xz.xed;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.*;

import static ax.xz.xed.xed_1.xed_inst_operand;
import static ax.xz.xed.xed_5.xed_inst_get_attributes;
import static java.util.Objects.requireNonNull;

public record XedInstruction(List<XedOperand> operands,
			     int cpl,
			     FlagInfo flagInfo,
			     Optional<InstructionException> exception,
			     InstructionIform iform,
			     Set<XedAttribute> attributes) {
	public XedInstruction {
		requireNonNull(operands);

		if (cpl != 0 && cpl != 3)
			throw new IllegalArgumentException("Invalid CPL " + cpl);
	}

	public static XedInstruction from(MemorySegment m) {
		return new XedInstruction(
			getInstructionOperands(m),
			getCpl(m),
			FlagInfo.fromInstruction(m),
			InstructionException.from(xed_inst_s._exceptions(m)),
			InstructionIform.from(xed_inst_s._iform_enum(m)),
			getAttributes(m));
	}

	private static List<XedOperand> getInstructionOperands(MemorySegment m) {
		var operandCount = xed_inst_s._noperands(m);
		var operands = new ArrayList<XedOperand>();

		for (int i = 0; i < operandCount; i++) {
			operands.add(XedOperand.from(xed_inst_operand(m, i)));
		}
		return Collections.unmodifiableList(operands);
	}

	private static int getCpl(MemorySegment m) {
		return xed_inst_s._cpl(m);
	}

	private static Set<XedAttribute> getAttributes(MemorySegment m) {
		var result = EnumSet.noneOf(XedAttribute.class);

		try (var allocator = Arena.ofConfined()) {
			var a = xed_inst_get_attributes(allocator, m);

			for (var attr : XedAttribute.values()) {
				boolean included = false;

				long one = 1;
				if (attr.xedEnum() < 64)
					included = (xed_attributes_t.a1(a) & (one << attr.xedEnum())) != 0;
				else
					included = (xed_attributes_t.a2(a) & (one << (attr.xedEnum() - 64))) != 0;

				if (included)
					result.add(attr);
			}
		}

		return result;
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", iform.name() + "[", "]")
			.add("attributes=" + attributes)
			.add("exception=" + exception)
			.add("flagInfo=" + flagInfo)
			.add("cpl=" + cpl)
			.add("operands=" + operands)
			.toString();
	}
}
