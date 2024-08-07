package ax.xz.fuzz.x86.operand;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;

import java.util.random.RandomGenerator;

public record VSIB(int indexWidth, RegisterSet possibleRegisters) implements Operand {
	@Override
	public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
	}

	@Override
	public boolean fulfilledBy(ResourcePartition partition) {
		return false; // TODO: implement
	}

	@Override
	public boolean counted() {
		return true;
	}

}
