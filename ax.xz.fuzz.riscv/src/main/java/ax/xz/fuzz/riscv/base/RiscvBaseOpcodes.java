package ax.xz.fuzz.riscv.base;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class RiscvBaseOpcodes {
	public static List<RiscvBaseOpcode> ALL_RV32I = Stream.of(
		RiscvBaseOpcode.ArithmeticOpcode.values(), RiscvBaseOpcode.ArithmeticImmOpcode.values(),
		RiscvBaseOpcode.LoadOpcode.values(), RiscvBaseOpcode.StoreOpcode.values(),
		RiscvBaseOpcode.BranchOpcode.values(), RiscvBaseOpcode.JumpOpcode.values(),
		RiscvBaseOpcode.UimmOpcode.values(), RiscvBaseOpcode.EnvironmentOpcode.values()
	).<RiscvBaseOpcode>flatMap(n -> Arrays.stream(n)).toList();

	public static List<RiscvBaseOpcode> ALL_RV64I = Stream.of(
		RiscvBaseOpcode.ArithmeticOpcode.values(), RiscvBaseOpcode.ArithmeticImmOpcode.values(),
		RiscvBaseOpcode.LoadOpcode.values(), RiscvBaseOpcode.StoreOpcode.values(),
		RiscvBaseOpcode.BranchOpcode.values(), RiscvBaseOpcode.JumpOpcode.values(),
		RiscvBaseOpcode.UimmOpcode.values(), RiscvBaseOpcode.EnvironmentOpcode.values(),
		RiscvBaseOpcode.ArithmeticOpcode64.values(), RiscvBaseOpcode.ArithmeticImmOpcode64.values(),
		RiscvBaseOpcode.LoadOpcode64.values(), RiscvBaseOpcode.StoreOpcode64.values()
	).<RiscvBaseOpcode>flatMap(n -> Arrays.stream(n)).toList();
}
