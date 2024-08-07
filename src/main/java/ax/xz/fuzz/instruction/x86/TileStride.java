package ax.xz.fuzz.instruction.x86;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.Operand;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;

import java.util.random.RandomGenerator;

public record TileStride() implements Operand {
	@Override
	public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
		// todo: do this
	}

	@Override
	public boolean fulfilledBy(ResourcePartition partition) {
		return false;
	}

	@Override
	public boolean counted() {
		return true;
	}

}
