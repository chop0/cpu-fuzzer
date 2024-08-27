package ax.xz.fuzz.riscv.base;

import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.riscv.RiscvModule;
import ax.xz.fuzz.riscv.RiscvOpcode;
import ax.xz.fuzz.riscv.RiscvRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public sealed abstract class RiscvBaseModule implements RiscvModule {
	private static final int NUM_GPR = 32;

	protected final int xlenBytes;
	private final List<RiscvRegister> registers;

	protected RiscvBaseModule(int xlenBytes) {
		this.xlenBytes = xlenBytes;

		var registers = new ArrayList<RiscvBaseRegister>();
		registers.add(new RiscvBaseRegister.Pc(this));

		for (int i = 0; i < NUM_GPR; i++) {
			registers.add(new RiscvBaseRegister.Gpr(i));
		}

		this.registers = Collections.unmodifiableList(registers);
	}

	@Override
	public List<RiscvRegister> registers() {
		return registers;
	}

	public final int xlenBytes() {
		return xlenBytes;
	}

	public RegisterDescriptor gpr(int index) {
		return registers.get(index + 1);
	}

	public static final class RV32I extends RiscvBaseModule {
		public RV32I() {
			super(4);
		}

		@Override
		public List<? extends RiscvOpcode> opcodes() {
			return RiscvBaseOpcodes.ALL_RV32I;
		}

		@Override
		public String toString() {
			return "rv32i (%d)".formatted(opcodes().size());
		}
	}

	public static final class RV64I extends RiscvBaseModule {
		public RV64I() {
			super(8);
		}

		@Override
		public List<? extends RiscvOpcode> opcodes() {
			return RiscvBaseOpcodes.ALL_RV64I;
		}

		@Override
		public String toString() {
			return "rv64i (%d)".formatted(opcodes().size());
		}
	}
}
