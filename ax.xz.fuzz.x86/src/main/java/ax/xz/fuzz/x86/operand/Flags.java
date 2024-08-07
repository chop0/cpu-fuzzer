package ax.xz.fuzz.x86.operand;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.instruction.StatusFlag;
import com.github.icedland.iced.x86.Instruction;

import java.util.random.RandomGenerator;

public record Flags(StatusFlag flag) implements Operand {

	@Override
	public boolean fulfilledBy(ResourcePartition partition) {
		return partition.statusFlags().contains(flag);
	}

	@Override
	public boolean counted() {
		return false;
	}

	@Override
	public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {

	}

}
