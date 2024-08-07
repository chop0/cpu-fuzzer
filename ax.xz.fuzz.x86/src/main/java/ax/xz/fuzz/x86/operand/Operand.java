package ax.xz.fuzz.x86.operand;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.InstructionBuilder;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;

import java.util.random.RandomGenerator;

public interface Operand {
	boolean fulfilledBy(ResourcePartition partition);
	boolean counted();
	void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException;
}
