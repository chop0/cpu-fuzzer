package ax.xz.fuzz.riscv.base;

import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.riscv.RiscvArchitecture;
import ax.xz.fuzz.riscv.RiscvRegister;

import static ax.xz.fuzz.arch.Architecture.activeArchitecture;

public sealed interface RiscvBaseRegister extends RiscvRegister {
	record Gpr(int index) implements RiscvBaseRegister {
		public Gpr {
			if (index < 0 || index >= 32) {
				throw new IllegalArgumentException("Invalid GPR index: " + index);
			}
		}

		@Override
		public int widthBytes() {
			return ((RiscvArchitecture)activeArchitecture()).baseModule().xlenBytes();
		}

		@Override
		public RegisterSet related() {
			return RegisterSet.of(this);
		}

		@Override
		public String toString() {
			return "x" + index;
		}
	}

	record Pc(RiscvBaseModule base) implements RiscvBaseRegister {
		@Override
		public int widthBytes() {
			return base.xlenBytes();
		}

		@Override
		public RegisterSet related() {
			return RegisterSet.of(this);
		}

		@Override
		public String toString() {
			return "pc";
		}
	}
}
