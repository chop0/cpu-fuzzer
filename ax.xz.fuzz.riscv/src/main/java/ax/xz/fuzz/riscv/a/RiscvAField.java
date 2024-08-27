package ax.xz.fuzz.riscv.a;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.riscv.RiscvInstructionBuilder;
import ax.xz.fuzz.riscv.RiscvInstructionField;

import java.util.random.RandomGenerator;

import static ax.xz.fuzz.riscv.InstructionUtils.pickBits;
import static ax.xz.fuzz.riscv.InstructionUtils.setBits;

enum RiscvAField implements RiscvInstructionField {
	FUNCT5(27, 31),
	AQ(26, 26),
	RL(25, 25);

	private final int bitStart;
	private final int bitEnd;

	RiscvAField(int bitStart, int bitEnd) {
		this.bitStart = bitStart;
		this.bitEnd = bitEnd;
	}


	@Override
	public int width() {
		return bitEnd - bitStart + 1;
	}

	@Override
	public int get(int instruction) {
		return pickBits(instruction, bitStart, bitEnd);
	}

	@Override
	public int apply(int instruction, int value) {
		return setBits(instruction, bitStart, bitEnd, value);
	}

	@Override
	public void select(RiscvInstructionBuilder builder, RandomGenerator rng, ResourcePartition resourcePartition) throws NoPossibilitiesException {
		var value = switch (this) {
			case AQ, RL -> rng.nextBoolean() ? 0 : 1;
			case FUNCT5 -> throw new UnsupportedOperationException("funct5 field cannot be selected");
		};

		builder.setField(this, value);
	}

	@Override
	public boolean fulfilledBy(ResourcePartition rp) {
		return true;
	}
}
