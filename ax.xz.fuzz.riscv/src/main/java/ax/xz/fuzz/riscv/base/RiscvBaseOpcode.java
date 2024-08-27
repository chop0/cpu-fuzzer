package ax.xz.fuzz.riscv.base;

import ax.xz.fuzz.riscv.InstructionUtils;
import ax.xz.fuzz.riscv.RiscvInstructionField;
import ax.xz.fuzz.riscv.RiscvOpcode;

import java.util.Map;

import static ax.xz.fuzz.riscv.RiscvInstructionField.OPCODE;
import static ax.xz.fuzz.riscv.base.RiscvBaseField.*;
import static ax.xz.fuzz.riscv.base.RiscvBaseFormat.*;

public sealed interface RiscvBaseOpcode extends RiscvOpcode {
	@Override
	RiscvBaseFormat format();

	@Override
	default String disassemble(int instruction) {
		return switch (format()) {
			case R -> String.format("%s %s, %s, %s", mnemonic(), InstructionUtils.gprStr(instruction, RD), InstructionUtils.gprStr(instruction, RS1), InstructionUtils.gprStr(instruction, RS2));
			case I -> String.format("%s %s, %s, %d", mnemonic(), InstructionUtils.gprStr(instruction, RD), InstructionUtils.gprStr(instruction, RS1), IMM_I_UNCONSTRAINED.getSigned(instruction));
			case I_SPLIT -> String.format("%s %s, %s, %x", mnemonic(), InstructionUtils.gprStr(instruction, RD), InstructionUtils.gprStr(instruction, RS1), IMM_I_04.get(instruction));
			case S -> String.format("%s %s, %s, %x", mnemonic(), InstructionUtils.gprStr(instruction, RS1), InstructionUtils.gprStr(instruction, RS2), IMM_S.get(instruction));
			case B -> String.format("%s %s, %s, %d", mnemonic(), InstructionUtils.gprStr(instruction, RS1), InstructionUtils.gprStr(instruction, RS2), IMM_B.getSigned(instruction));
			case U -> String.format("%s %s, %x", mnemonic(), InstructionUtils.gprStr(instruction, RD), IMM_U.get(instruction));
			case J -> String.format("%s %s, %x", mnemonic(), InstructionUtils.gprStr(instruction, RD), IMM_J.get(instruction));
		};
	}

	@Override
	default String mnemonic() {
		return toString().toLowerCase();
	}

	@Override
	default boolean isControlFlow() {
		return false;
	}

	enum ArithmeticOpcode implements RiscvBaseOpcode {
		ADD(0x0, 0x00),
		SUB(0x0, 0x20),
		XOR(0x4, 0x00),
		OR(0x6, 0x00),
		AND(0x7, 0x00),
		SLL(0x1, 0x00),
		SRL(0x5, 0x00),
		SRA(0x5, 0x20),
		SLT(0x2, 0x00),
		SLTU(0x3, 0x00);

		private final RiscvBaseFormat format;
		private final Map<RiscvInstructionField, Integer> fieldConstraints;

		ArithmeticOpcode(int funct3, int funct7) {
			this.format = R;
			this.fieldConstraints = Map.of(
				OPCODE, 0b0110011,
				FUNCT3, funct3,
				FUNCT7, funct7
			);
		}

		@Override
		public RiscvBaseFormat format() {
			return format;
		}

		@Override
		public Map<RiscvInstructionField, Integer> fieldConstraints() {
			return fieldConstraints;
		}
	}

	enum ArithmeticImmOpcode implements RiscvBaseOpcode {
		ADDI(0x0),
		XORI(0x4),
		ORI(0x6),
		ANDI(0x7),
		SLLI(0x1, 0x00),
		SRLI(0x5, 0x00),
		SRAI(0x5, 0x20),
		SLTI(0x2),
		SLTIU(0x3);

		private final RiscvBaseFormat format;
		private final Map<RiscvInstructionField, Integer> fieldConstraints;

		ArithmeticImmOpcode(int funct3) {
			this.format = I;
			this.fieldConstraints = Map.of(
				OPCODE, 0b0010011,
				FUNCT3, funct3
			);
		}

		ArithmeticImmOpcode(int funct3, int imm511) {
			this.format = I_SPLIT;

			this.fieldConstraints = Map.of(
				OPCODE, 0b0010011,
				FUNCT3, funct3,
				IMM_I_511, imm511
			);
		}

		@Override
		public RiscvBaseFormat format() {
			return format;
		}

		@Override
		public Map<RiscvInstructionField, Integer> fieldConstraints() {
			return fieldConstraints;
		}
	}

	enum LoadOpcode implements RiscvBaseOpcode {
		LB(0x0),
		LH(0x1),
		LW(0x2),
		LBU(0x4),
		LHU(0x5);

		private final RiscvBaseFormat format;
		private final Map<RiscvInstructionField, Integer> fieldConstraints;

		LoadOpcode(int funct3) {
			this.format = I;
			this.fieldConstraints = Map.of(
				OPCODE, 0b0000011,
				FUNCT3, funct3
			);
		}

		@Override
		public RiscvBaseFormat format() {
			return format;
		}

		@Override
		public Map<RiscvInstructionField, Integer> fieldConstraints() {
			return fieldConstraints;
		}
	}

	enum StoreOpcode implements RiscvBaseOpcode {
		SB(0x0),
		SH(0x1),
		SW(0x2);

		private final RiscvBaseFormat format;
		private final Map<RiscvInstructionField, Integer> fieldConstraints;

		StoreOpcode(int funct3) {
			this.format = S;
			this.fieldConstraints = Map.of(
				OPCODE, 0b0100011,
				FUNCT3, funct3
			);
		}

		@Override
		public RiscvBaseFormat format() {
			return format;
		}

		@Override
		public Map<RiscvInstructionField, Integer> fieldConstraints() {
			return fieldConstraints;
		}
	}

	enum BranchOpcode implements RiscvBaseOpcode {
		BEQ(0x0),
		BNE(0x1),
		BLT(0x4),
		BGE(0x5),
		BLTU(0x6),
		BGEU(0x7);

		private final RiscvBaseFormat format;
		private final Map<RiscvInstructionField, Integer> fieldConstraints;

		BranchOpcode(int funct3) {
			this.format = B;
			this.fieldConstraints = Map.of(
				OPCODE, 0b1100011,
				FUNCT3, funct3
			);
		}

		@Override
		public RiscvBaseFormat format() {
			return format;
		}

		@Override
		public Map<RiscvInstructionField, Integer> fieldConstraints() {
			return fieldConstraints;
		}

		public String disassemble(int instruction, String targetLabel) {
			return String.format("%s %s, %s, %s", mnemonic(), InstructionUtils.gprStr(instruction, RS1), InstructionUtils.gprStr(instruction, RS2), targetLabel);
		}

		@Override
		public boolean isControlFlow() {
			return true;
		}
	}

	enum JumpOpcode implements RiscvBaseOpcode {
		JAL(J, 0b1101111),
		JALR(I, 0b1100111, 0);

		private final RiscvBaseFormat format;
		private final Map<RiscvInstructionField, Integer> fieldConstraints;

		JumpOpcode(RiscvBaseFormat format, int opcode, int funct3) {
			this.format = format;
			this.fieldConstraints = Map.of(
				OPCODE, opcode,
				FUNCT3, funct3
			);
		}

		JumpOpcode(RiscvBaseFormat format, int opcode) {
			this.format = format;
			this.fieldConstraints = Map.of(
				OPCODE, opcode
			);
		}

		@Override
		public RiscvBaseFormat format() {
			return format;
		}

		@Override
		public Map<RiscvInstructionField, Integer> fieldConstraints() {
			return fieldConstraints;
		}

		@Override
		public boolean isControlFlow() {
			return true;
		}
	}

	enum UimmOpcode implements RiscvBaseOpcode {
		LUI(U, 0b0110111),
		AUIPC(U, 0b0010111);

		private final RiscvBaseFormat format;
		private final Map<RiscvInstructionField, Integer> fieldConstraints;

		UimmOpcode(RiscvBaseFormat format, int opcode) {
			this.format = format;
			this.fieldConstraints = Map.of(
				OPCODE, opcode
			);
		}

		@Override
		public RiscvBaseFormat format() {
			return format;
		}

		@Override
		public Map<RiscvInstructionField, Integer> fieldConstraints() {
			return fieldConstraints;
		}
	}

	enum EnvironmentOpcode implements RiscvBaseOpcode {
		ECALL(0b1110011, 0x000, 0),
		EBREAK(0b1110011, 0x001, 1);

		private final RiscvBaseFormat format;
		private final Map<RiscvInstructionField, Integer> fieldConstraints;

		EnvironmentOpcode(int opcode, int funct3, int imm) {
			this.format = I;
			this.fieldConstraints = Map.of(
				OPCODE, opcode,
				FUNCT3, funct3,
				IMM_I_UNCONSTRAINED, imm
			);
		}

		@Override
		public RiscvBaseFormat format() {
			return format;
		}

		@Override
		public Map<RiscvInstructionField, Integer> fieldConstraints() {
			return fieldConstraints;
		}

		@Override
		public boolean isControlFlow() {
			return true;
		}
	}

	enum ArithmeticOpcode64 implements RiscvBaseOpcode {
		ADDW(0x0, 0x00),
		SUBW(0x0, 0x20),
		SLLW(0x1, 0x00),
		SRLW(0x5, 0x00),
		SRAW(0x5, 0x20);

		private final RiscvBaseFormat format;
		private final Map<RiscvInstructionField, Integer> fieldConstraints;

		ArithmeticOpcode64(int funct3, int funct7) {
			this.format = R;
			this.fieldConstraints = Map.of(
				OPCODE, 0b0111011,
				FUNCT3, funct3,
				FUNCT7, funct7
			);
		}

		@Override
		public RiscvBaseFormat format() {
			return format;
		}

		@Override
		public Map<RiscvInstructionField, Integer> fieldConstraints() {
			return fieldConstraints;
		}
	}

	enum ArithmeticImmOpcode64 implements RiscvBaseOpcode {
		ADDIW(0x0),
		SLLIW(0x1, 0x00),
		SRLIW(0x5, 0x00),
		SRAIW(0x5, 0x20);

		private final RiscvBaseFormat format;
		private final Map<RiscvInstructionField, Integer> fieldConstraints;

		ArithmeticImmOpcode64(int funct3) {
			this.format = I;
			this.fieldConstraints = Map.of(
				OPCODE, 0b0011011,
				FUNCT3, funct3
			);
		}

		ArithmeticImmOpcode64(int funct3, int imm511) {
			this.format = I_SPLIT;

			this.fieldConstraints = Map.of(
				OPCODE, 0b0011011,
				FUNCT3, funct3,
				IMM_I_511, imm511
			);
		}

		@Override
		public RiscvBaseFormat format() {
			return format;
		}

		@Override
		public Map<RiscvInstructionField, Integer> fieldConstraints() {
			return fieldConstraints;
		}
	}

	enum LoadOpcode64 implements RiscvBaseOpcode {
		LWU(0x6),
		LD(0x3);

		private final RiscvBaseFormat format;
		private final Map<RiscvInstructionField, Integer> fieldConstraints;

		LoadOpcode64(int funct3) {
			this.format = I;
			this.fieldConstraints = Map.of(
				OPCODE, 0b0000011,
				FUNCT3, funct3
			);
		}

		@Override
		public RiscvBaseFormat format() {
			return format;
		}

		@Override
		public Map<RiscvInstructionField, Integer> fieldConstraints() {
			return fieldConstraints;
		}
	}

	enum StoreOpcode64 implements RiscvBaseOpcode {
		SD(0x3);

		private final RiscvBaseFormat format;
		private final Map<RiscvInstructionField, Integer> fieldConstraints;

		StoreOpcode64(int funct3) {
			this.format = S;
			this.fieldConstraints = Map.of(
				OPCODE, 0b0100011,
				FUNCT3, funct3
			);
		}

		@Override
		public RiscvBaseFormat format() {
			return format;
		}

		@Override
		public Map<RiscvInstructionField, Integer> fieldConstraints() {
			return fieldConstraints;
		}
	}
}
