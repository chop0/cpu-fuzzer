package ax.xz.fuzz.mutate;

import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;

import java.util.random.RandomGenerator;

public interface Mutator {
	boolean appliesTo(Instruction instruction, ResourcePartition rp);
	DeferredMutation createMutation(Instruction instruction, RandomGenerator rng, ResourcePartition rp);
}
