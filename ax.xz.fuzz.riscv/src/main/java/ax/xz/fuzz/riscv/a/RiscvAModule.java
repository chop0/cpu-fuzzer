package ax.xz.fuzz.riscv.a;

import ax.xz.fuzz.riscv.RiscvModule;
import ax.xz.fuzz.riscv.RiscvOpcode;
import ax.xz.fuzz.riscv.RiscvRegister;

import java.util.List;

public final class RiscvAModule implements RiscvModule {
	private static final RiscvAModule RV64A = new RiscvAModule(true);
	private static final RiscvAModule RV32A = new RiscvAModule(false);

	private final boolean is64;

	private RiscvAModule(boolean is64) {
		this.is64 = is64;
	}

	@Override
	public List<? extends RiscvRegister> registers() {
		return List.of();
	}

	@Override
	public List<? extends RiscvOpcode> opcodes() {
		return is64 ? RiscvAOpcode.rv64a : RiscvAOpcode.rv32a;
	}

	public static RiscvAModule rv64a() {
		return RV64A;
	}

	public static RiscvAModule rv32m() {
		return RV64A;
	}

	@Override
	public String toString() {
		return "rv%da (%d)".formatted(is64 ? 64 : 32, opcodes().size());
	}
}
