package ax.xz.fuzz.riscv;

import ax.xz.fuzz.riscv.base.RiscvBaseFormat;

import java.util.List;

public sealed interface RiscvInstructionFormat permits RiscvBaseFormat {
	List<RiscvInstructionField> fields();
}
