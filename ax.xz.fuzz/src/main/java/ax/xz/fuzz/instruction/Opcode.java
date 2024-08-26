package ax.xz.fuzz.instruction;

import ax.xz.fuzz.blocks.NoPossibilitiesException;

import java.util.random.RandomGenerator;

public interface Opcode {

	InstructionBuilder select(RandomGenerator rng, ResourcePartition resourcePartition) throws NoPossibilitiesException;

	boolean fulfilledBy(ResourcePartition rp);

	String mnemonic();
}
