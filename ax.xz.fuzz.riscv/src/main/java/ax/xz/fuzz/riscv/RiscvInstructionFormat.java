package ax.xz.fuzz.riscv;

import ax.xz.fuzz.riscv.base.RiscvBaseFormat;

import java.util.List;

public interface RiscvInstructionFormat {
	List<RiscvInstructionField> fields();
}
