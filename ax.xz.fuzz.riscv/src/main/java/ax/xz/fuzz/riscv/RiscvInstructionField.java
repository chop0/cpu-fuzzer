package ax.xz.fuzz.riscv;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.riscv.base.RiscvBaseField;

import java.util.random.RandomGenerator;

public interface RiscvInstructionField {
	String name();

	int width();
	int get(int instruction);
	int apply(int instruction, int value);

	void select(RiscvInstructionBuilder builder, RandomGenerator rng, ResourcePartition resourcePartition) throws NoPossibilitiesException;
	boolean fulfilledBy(ResourcePartition rp);

	record OpcodeField() implements RiscvInstructionField {
		@Override
		public String name() {
			return "opcode";
		}

		@Override
		public int width() {
			return 7;
		}

		@Override
		public int get(int instruction) {
			return instruction & 0b1111111;
		}

		@Override
		public int apply(int instruction, int value) {
			return (instruction & ~0b1111111) | (value & 0b1111111);
		}

		@Override
		public void select(RiscvInstructionBuilder builder, RandomGenerator rng, ResourcePartition resourcePartition) {
			throw new UnsupportedOperationException("opcode field cannot be selected");
		}

		@Override
		public boolean fulfilledBy(ResourcePartition rp) {
			return true;
		}
	}

	OpcodeField OPCODE = new OpcodeField();
}
