package ax.xz.fuzz.riscv;

import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.riscv.base.RiscvBaseRegister;

public sealed interface RiscvRegister extends RegisterDescriptor permits RiscvBaseRegister {
}
