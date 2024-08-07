package ax.xz.fuzz.instruction;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import com.github.icedland.iced.x86.Instruction;

import java.util.random.RandomGenerator;

public interface Operand {
	boolean fulfilledBy(ResourcePartition partition);
	boolean counted();
	void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException;
}
