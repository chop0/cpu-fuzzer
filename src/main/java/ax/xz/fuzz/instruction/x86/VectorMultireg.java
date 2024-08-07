package ax.xz.fuzz.instruction.x86;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.Operand;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.OpKind;

import java.util.random.RandomGenerator;

public record VectorMultireg(int count, RegisterSet possibleStartRegisters) implements Operand {
	private RegisterSet intersection(ResourcePartition partition) {
		return partition.allowedRegisters().consecutiveBlocks(count, possibleStartRegisters);
	}

	@Override
	public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
		int register = ((x86RegisterDescriptor)intersection(rp).select(random)).icedId();
		instruction.setOpKind(operandIndex, OpKind.REGISTER);
		instruction.setOpRegister(operandIndex, register);
	}

	@Override
	public boolean fulfilledBy(ResourcePartition partition) {
		return !intersection(partition).isEmpty();
	}

	@Override
	public boolean counted() {
		return true;
	}

}
