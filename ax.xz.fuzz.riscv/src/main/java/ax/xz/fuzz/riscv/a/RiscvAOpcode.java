package ax.xz.fuzz.riscv.a;

import ax.xz.fuzz.riscv.RiscvInstructionField;
import ax.xz.fuzz.riscv.RiscvInstructionFormat;
import ax.xz.fuzz.riscv.RiscvOpcode;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static ax.xz.fuzz.riscv.InstructionUtils.gprStr;
import static ax.xz.fuzz.riscv.RiscvInstructionField.OPCODE;
import static ax.xz.fuzz.riscv.a.RiscvAField.FUNCT5;
import static ax.xz.fuzz.riscv.a.RiscvAFormat.R_A;
import static ax.xz.fuzz.riscv.base.RiscvBaseField.*;

sealed interface RiscvAOpcode extends RiscvOpcode {
	List<AOpcode> rv32a = Arrays.stream(ABaseOpcode.values())
		.map(base -> new AOpcode(0b010, base))
		.toList();

	List<AOpcode> rv64a = Stream.concat(
		rv32a.stream(),
		Arrays.stream(ABaseOpcode.values())
			.map(base -> new AOpcode(0b011, base))
	).toList();

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

	enum ABaseOpcode {
		LR(0b00010),
		SC(0b00011),
		AMOSWAP(0b00001),
		AMOADD(0b00000),
		AMOXOR(0b00100),
		AMOAND(0b01100),
		AMOOR(0b01000),
		AMOMIN(0b10000),
		AMOMAX(0b10100),
		AMOMINU(0b11000),
		AMOMAXU(0b11100);

		private final int funct5;

		ABaseOpcode(int funct5) {
			this.funct5 = funct5;
		}
	}

	record AOpcode(int funct3, ABaseOpcode base) implements RiscvAOpcode {
		public AOpcode {
			if (funct3 != 0b010 && funct3 != 0b011)
				throw new IllegalArgumentException("Invalid funct3 for AOpcode: " + funct3);
		}

		boolean is64() {
			return funct3 == 0b011;
		}

		@Override
		public RiscvInstructionFormat format() {
			return R_A;
		}

		@Override
		public Map<RiscvInstructionField, Integer> fieldConstraints() {
			return switch (base) {
				case LR -> Map.of(
					OPCODE, 0b0101111,
					FUNCT3, funct3,
					FUNCT5, base.funct5,
					RS2, 0
				);

				default -> Map.of(
					OPCODE, 0b0101111,
					FUNCT3, funct3,
					FUNCT5, base.funct5
				);
			};
		}

		@Override
		public String mnemonic() {
			return base.toString().toLowerCase() + (is64() ? ".d" : ".w");
		}
	}
}
