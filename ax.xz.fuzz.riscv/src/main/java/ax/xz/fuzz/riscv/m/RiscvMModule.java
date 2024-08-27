package ax.xz.fuzz.riscv.m;

import ax.xz.fuzz.riscv.RiscvModule;
import ax.xz.fuzz.riscv.RiscvOpcode;
import ax.xz.fuzz.riscv.RiscvRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RiscvMModule implements RiscvModule {
	private static final RiscvMModule RV32M = new RiscvMModule(false);
	private static final RiscvMModule RV64M = new RiscvMModule(true);

	private static final List<RiscvMOpcode> opcodes32, opcodes64;

	static  {
		opcodes32 = List.of(RiscvMOpcode.MOpcode32.values());

		var op64 = new ArrayList<RiscvMOpcode>();
		op64.addAll(List.of(RiscvMOpcode.MOpcode32.values()));
		op64.addAll(List.of(RiscvMOpcode.MOpcode64.values()));
		opcodes64 = Collections.unmodifiableList(op64);
	}

	private final boolean is64;

	private RiscvMModule(boolean is64) {
		this.is64 = is64;
	}

	@Override
	public List<? extends RiscvRegister> registers() {
		return List.of();
	}

	@Override
	public List<? extends RiscvOpcode> opcodes() {
		return is64 ? opcodes64 : opcodes32;
	}

	public static RiscvMModule rv64m() {
		return RV32M;
	}

	public static RiscvMModule rv32m() {
		return RV64M;
	}

	@Override
	public String toString() {
		return is64 ? "rv64m" : "rv32m";
	}
}
