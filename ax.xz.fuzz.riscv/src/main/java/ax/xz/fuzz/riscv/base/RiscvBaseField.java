package ax.xz.fuzz.riscv.base;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.riscv.InstructionUtils;
import ax.xz.fuzz.riscv.RiscvInstructionBuilder;
import ax.xz.fuzz.riscv.RiscvInstructionField;

import java.util.random.RandomGenerator;

import static ax.xz.fuzz.riscv.InstructionUtils.pickBits;

public enum RiscvBaseField implements RiscvInstructionField {
	RD(5),
	FUNCT3(3),
	RS1(5),
	RS2(5),
	FUNCT7(7),

	IMM_I_UNCONSTRAINED(12),
	IMM_I_511(7),
	IMM_I_04(5),

	IMM_S(12),
	IMM_B(12),
	IMM_U(20),
	IMM_J(20);

	private final int width;

	RiscvBaseField(int width) {
		this.width = width;
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public int get(int instruction) {
		return switch (this) {
			case RD -> pickBits(instruction, 7, 11);
			case FUNCT3 -> pickBits(instruction, 12, 14);
			case RS1 -> pickBits(instruction, 15, 19);
			case RS2 -> pickBits(instruction, 20, 24);
			case FUNCT7 -> pickBits(instruction, 25, 31);

			case IMM_I_UNCONSTRAINED -> pickBits(instruction, 20, 31);
			case IMM_I_04 -> pickBits(instruction, 20, 24);

			case IMM_I_511 -> pickBits(instruction, 25, 31);

			case IMM_S -> pickBits(instruction, 7, 11) | (pickBits(instruction, 25, 31) << 5);
			case IMM_B -> pickBits(instruction, 7, 7) << 11 | pickBits(instruction, 8, 11) << 1 | pickBits(instruction, 25, 30) << 5 | pickBits(instruction, 31, 31) << 12;

			case IMM_U -> pickBits(instruction, 12, 31);
			case IMM_J -> pickBits(instruction, 12, 19) << 12 | pickBits(instruction, 20, 20) << 11 | pickBits(instruction, 21, 30) << 1 | pickBits(instruction, 31, 31) << 20;
		};
	}

	public int getSigned(int instruction) {
		int value = get(instruction);
		int sign = 1 << (width - 1);
		return (value ^ sign) - sign;
	}

	@Override
	public int apply(int instruction, int value) {
		return switch (this) {
			case RD -> InstructionUtils.setBits(instruction, 7, 11, value);
			case FUNCT3 -> InstructionUtils.setBits(instruction, 12, 14, value);
			case RS1 -> InstructionUtils.setBits(instruction, 15, 19, value);
			case RS2 -> InstructionUtils.setBits(instruction, 20, 24, value);
			case FUNCT7 -> InstructionUtils.setBits(instruction, 25, 31, value);

			case IMM_I_UNCONSTRAINED -> InstructionUtils.setBits(instruction, 20, 31, value);
			case IMM_I_511 -> InstructionUtils.setBits(instruction, 25, 31, value);

			case IMM_I_04 -> InstructionUtils.setBits(instruction, 20, 24, value);

			case IMM_S -> InstructionUtils.setBits(
				InstructionUtils.setBits(instruction, 7, 11, pickBits(value, 0, 4)),
				25, 31, pickBits(value, 5, 11)
			);

			case IMM_B -> InstructionUtils.setBits(
				InstructionUtils.setBits(
					InstructionUtils.setBits(
						InstructionUtils.setBits(instruction, 7, 7, pickBits(value, 11, 11)),
						8, 11, pickBits(value, 1, 4)
					),
					25, 30, pickBits(value, 5, 10)
				),
				31, 31, pickBits(value, 12, 12)
			);


			case IMM_U -> InstructionUtils.setBits(instruction, 12, 31, value);
			case IMM_J -> InstructionUtils.setBits(
				InstructionUtils.setBits(
					InstructionUtils.setBits(
						InstructionUtils.setBits(instruction, 12, 19, pickBits(value, 12, 19)),
						20, 20, pickBits(value, 11, 11)
					),
					21, 30, pickBits(value, 1, 10)
				),
				31, 31, pickBits(value, 20, 20)
			);
		};
	}

	@Override
	public void select(RiscvInstructionBuilder builder, RandomGenerator rng, ResourcePartition resourcePartition) throws NoPossibilitiesException {
		var a = builder.architecture();

		var value = switch (this) {
			case RD, RS1, RS2 -> a.registerIndex(resourcePartition.selectRegister(builder.architecture().gprs(), rng));
			case 	IMM_S, IMM_B, IMM_U, IMM_J,
				IMM_I_UNCONSTRAINED -> rng.nextInt() & ((1 << (width + 1)) - 1);
			case IMM_I_04 -> rng.nextInt() & ((1 << 5) - 1);
			case IMM_I_511 -> rng.nextInt() & ((1 << 7) - 1);

			case FUNCT3, FUNCT7 -> throw new UnsupportedOperationException("Cannot select value for funct3 or funct7");
		};

		builder.setField(this, value);
	}

	@Override
	public boolean fulfilledBy(ResourcePartition rp) {
		return switch (this) {
			case RD, RS1, RS2 -> !rp.allowedRegisters().isEmpty();
			case FUNCT3, FUNCT7 -> true;
			case IMM_S, IMM_B, IMM_U, IMM_J, IMM_I_UNCONSTRAINED, IMM_I_04, IMM_I_511 -> true;
		};
	}
}
