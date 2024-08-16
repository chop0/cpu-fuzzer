package ax.xz.fuzz.x86.operand;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;

import java.util.random.RandomGenerator;

public record Imm(int bitSize, int immediateOpType) implements Operand {
	@Override
	public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
		instruction.setOpKind(operandIndex, immediateOpType);

		long immediate = random.nextLong() >>> (64 - bitSize);
		instruction.setImmediate(operandIndex, immediate);
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
