package ax.xz.fuzz.riscv.m;

import ax.xz.fuzz.riscv.RiscvInstructionField;
import ax.xz.fuzz.riscv.RiscvInstructionFormat;
import ax.xz.fuzz.riscv.RiscvOpcode;

import java.util.Map;

import static ax.xz.fuzz.riscv.RiscvInstructionField.OPCODE;
import static ax.xz.fuzz.riscv.base.RiscvBaseField.*;
import static ax.xz.fuzz.riscv.base.RiscvBaseFormat.R;
import static ax.xz.fuzz.riscv.base.RiscvBaseOpcode.gprStr;

public sealed interface RiscvMOpcode extends RiscvOpcode {
	@Override
	default String disassemble(int instruction) {
		return String.format("%s %s, %s, %s", mnemonic(), gprStr(instruction, RD), gprStr(instruction, RS1), gprStr(instruction, RS2));
	}

	@Override
	default boolean isControlFlow() {
		return false;
	}

	@Override
	default String mnemonic() {
		return toString().toLowerCase();
	}

	enum MOpcode32 implements RiscvMOpcode {
		MUL(0x0, 0x1),
		MULH(0x1, 0x1),
		MULHSU(0x2, 0x1),
		MULHU(0x3, 0x1),

		DIV(0x4, 0x1),
		DIVU(0x5, 0x1),
		REM(0x6, 0x1),
		REMU(0x7, 0x1);

		private final int funct3;
		private final int funct7;

		MOpcode32(int funct3, int funct7) {
			this.funct3 = funct3;
			this.funct7 = funct7;
		}

		@Override
		public RiscvInstructionFormat format() {
			return R;
		}

		@Override
		public Map<RiscvInstructionField, Integer> fieldConstraints() {
			return Map.of(
				OPCODE, 0b0110011,
				FUNCT3, funct3,
				FUNCT7, funct7
			);
		}
	}

	enum MOpcode64 implements RiscvMOpcode {
		MULW(0x0, 0x1),
		DIVW(0x4, 0x1),
		DIVUW(0x5, 0x1),
		REMW(0x6, 0x1),
		REMUW(0x7, 0x1);

		private final int funct3;
		private final int funct7;

		MOpcode64(int funct3, int funct7) {
			this.funct3 = funct3;
			this.funct7 = funct7;
		}

		@Override
		public RiscvInstructionFormat format() {
			return R;
		}

		@Override
		public Map<RiscvInstructionField, Integer> fieldConstraints() {
			return Map.of(
				OPCODE, 0b0111011,
				FUNCT3, funct3,
				FUNCT7, funct7
			);
		}
	}
}
