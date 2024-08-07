package ax.xz.fuzz.instruction;

import ax.xz.fuzz.blocks.NoPossibilitiesException;

import java.util.random.RandomGenerator;

public abstract class Opcode {
	protected final String mnemonic;

	public Opcode(String mnemonic) {
		this.mnemonic = mnemonic;
	}

	public abstract InstructionBuilder configureRandomly(RandomGenerator random, ResourcePartition rp);

	public abstract InstructionBuilder select(RandomGenerator rng, ResourcePartition resourcePartition) throws NoPossibilitiesException;

	public abstract boolean fulfilledBy(ResourcePartition rp);

	public String mnemonic() {
		return mnemonic;
	}
}
