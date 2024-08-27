package ax.xz.fuzz.riscv.base;

import ax.xz.fuzz.riscv.RiscvInstructionField;
import ax.xz.fuzz.riscv.RiscvInstructionFormat;

import java.util.List;

import static ax.xz.fuzz.riscv.RiscvInstructionField.OPCODE;
import static ax.xz.fuzz.riscv.base.RiscvBaseField.*;

public enum RiscvBaseFormat implements RiscvInstructionFormat {
	R(OPCODE, RD, FUNCT3, RS1, RS2, FUNCT7),
	I(OPCODE, RD, FUNCT3, RS1, IMM_I_UNCONSTRAINED),
	I_SPLIT(OPCODE, RD, FUNCT3, RS1, IMM_I_511, IMM_I_04),
	S(OPCODE, FUNCT3, RS1, RS2, IMM_S),
	B(OPCODE, FUNCT3, RS1, RS2, IMM_B),
	U(OPCODE, RD, IMM_U),
	J(OPCODE, RD, IMM_J);

	private final List<RiscvInstructionField> fields;

	RiscvBaseFormat(RiscvInstructionField... fields) {
		this.fields = List.of(fields);
	}

	@Override
	public List<RiscvInstructionField> fields() {
		return fields;
	}
}
