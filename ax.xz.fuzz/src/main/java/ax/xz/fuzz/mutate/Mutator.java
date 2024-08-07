package ax.xz.fuzz.mutate;

import ax.xz.fuzz.instruction.InstructionBuilder;
import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;

import java.util.random.RandomGenerator;

public interface Mutator<C extends Opcode, B extends InstructionBuilder> {
	boolean appliesTo(ResourcePartition rp, C code, B instruction);
	boolean comesFrom(ResourcePartition rp, C code, B instruction, DeferredMutation outcome);

	DeferredMutation select(RandomGenerator rng, ResourcePartition rp, B instruction);
}
