package ax.xz.fuzz.x86.operand;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.OpKind;

import java.util.random.RandomGenerator;

public record Moffs(int bitSize) implements Operand {
	@Override
	public void select(RandomGenerator random, Instruction instruction, int operandIdx, ResourcePartition rp) throws NoPossibilitiesException {
		instruction.setOpKind(operandIdx, OpKind.MEMORY);
		instruction.setMemoryDisplSize(4);
		instruction.setMemoryDisplacement32(random.nextInt());
	}

	@Override
	public boolean fulfilledBy(ResourcePartition partition) {
		return false; // todo: figure this out
	}

	@Override
	public boolean counted() {
		return true;
	}

}
