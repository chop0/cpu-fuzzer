package ax.xz.fuzz.riscv;

import ax.xz.fuzz.arch.BranchDescription;
import ax.xz.fuzz.riscv.base.RiscvBaseRegister;

import java.util.function.Consumer;

import static ax.xz.fuzz.riscv.base.RiscvBaseRegisters.x0;

public record RiscvBranch(RiscvBranchType type, RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2) implements BranchDescription {
	public void perform(RiscvAssembler assembler, Label target) {
		switch (type) {
			case BEQ -> assembler.beq(rs1, rs2, target);
			case BNE -> assembler.bne(rs1, rs2, target);
			case BLT -> assembler.blt(rs1, rs2, target);
			case BGE -> assembler.bge(rs1, rs2, target);
			case BLTU -> assembler.bltu(rs1, rs2, target);
			case BGEU -> assembler.bgeu(rs1, rs2, target);
		}
	}

	@Override
	public String asAssembler(int takenIndex, int notTakenIndex) {
		if (equals(unconditional()))
			return "j label%d".formatted(takenIndex);

		return "%s %s, %s, label%d\nj label%d".formatted(type.name().toLowerCase(), rs1, rs2, takenIndex,  notTakenIndex);
	}

	public enum RiscvBranchType {
		BEQ,
		BNE,
		BLT,
		BGE,
		BLTU,
		BGEU;
	}

	private static final RiscvBranch UNCONDITIONAL = new RiscvBranch(RiscvBranchType.BEQ, x0, x0);
	public static RiscvBranch unconditional() {
		return UNCONDITIONAL;
	}
}
