package ax.xz.fuzz.mutate;

import ax.xz.fuzz.blocks.randomisers.ReverseRandomGenerator;
import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;

import java.util.random.RandomGenerator;

public interface Mutator {
	boolean appliesTo(ResourcePartition rp, Opcode code, Instruction instruction);
	boolean comesFrom(ResourcePartition rp, Opcode code, Instruction instruction, DeferredMutation outcome);

	DeferredMutation select(RandomGenerator rng, ResourcePartition rp, Instruction instruction);
	void reverse(ReverseRandomGenerator rng, ResourcePartition rp, Instruction instruction, DeferredMutation outcome);
}
