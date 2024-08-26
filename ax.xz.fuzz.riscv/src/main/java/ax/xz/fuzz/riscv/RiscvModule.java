package ax.xz.fuzz.riscv;

import java.util.List;

public interface RiscvModule {
	List<? extends RiscvRegister> registers();
	List<? extends RiscvOpcode> opcodes();
}
