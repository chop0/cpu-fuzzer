package ax.xz.fuzz.riscv.a;

import ax.xz.fuzz.riscv.RiscvInstructionField;
import ax.xz.fuzz.riscv.RiscvInstructionFormat;

import java.util.List;

import static ax.xz.fuzz.riscv.RiscvInstructionField.OPCODE;
import static ax.xz.fuzz.riscv.a.RiscvAField.*;
import static ax.xz.fuzz.riscv.base.RiscvBaseField.*;

public enum RiscvAFormat implements RiscvInstructionFormat {
	R_A(FUNCT5, AQ, RL, RS2, RS1, FUNCT3, RD, OPCODE);

	private final List<RiscvInstructionField> fields;

	RiscvAFormat(RiscvInstructionField... fields) {
		this.fields = List.of(fields);
	}

	@Override
	public List<RiscvInstructionField> fields() {
		return fields;
	}
}
