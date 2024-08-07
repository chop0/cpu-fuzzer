package ax.xz.fuzz.instruction.x86;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.Operand;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.OpKind;

import java.util.random.RandomGenerator;

public record FixedNumber(byte value) implements Operand {

	@Override
	public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
		instruction.setOpKind(operandIndex, OpKind.IMMEDIATE8);
		instruction.setImmediate8(value);
	}

	@Override
	public boolean fulfilledBy(ResourcePartition partition) {
		return true;
	}

	@Override
	public boolean counted() {
		return true;
	}

}
