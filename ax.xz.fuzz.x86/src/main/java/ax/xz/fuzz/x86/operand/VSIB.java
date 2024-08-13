package ax.xz.fuzz.x86.operand;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.OpKind;

import java.util.random.RandomGenerator;

import static com.github.icedland.iced.x86.Register.NONE;

public record VSIB(int indexWidth, RegisterSet possibleRegisters) implements Operand {
	@Override
	public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
		instruction.setOpKind(operandIndex, OpKind.MEMORY);
		// todo:  support protected mode
		instruction.setMemoryBase(NONE);
		instruction.setMemoryIndex(NONE);
		instruction.setMemoryDisplSize(4);
		instruction.setMemoryDisplacement64(rp.selectAddress(random, indexWidth, indexWidth, 4));
	}

	@Override
	public boolean fulfilledBy(ResourcePartition partition) {
		return partition.canFulfil(indexWidth, indexWidth, 4);
	}

	@Override
	public boolean counted() {
		return true;
	}

}
