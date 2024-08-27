package ax.xz.fuzz.riscv;

import ax.xz.fuzz.instruction.RegisterDescriptor;

import static ax.xz.fuzz.arch.Architecture.activeArchitecture;
import static ax.xz.fuzz.riscv.base.RiscvBaseRegisters.x0;

public class InstructionUtils {
	public static RegisterDescriptor gprStr(int instruction, RiscvInstructionField field) {
		return activeArchitecture().registerByIndex(field.get(instruction) + activeArchitecture().registerIndex(x0));
	}

	public static int pickBits(int value, int startInclusive, int endInclusive) {
		int mask = (1 << (endInclusive - startInclusive + 1)) - 1;
		return (value >>> startInclusive) & mask;
	}

	public static int setBits(int value, int startInclusive, int endInclusive, int bits) {
		int mask = (1 << (endInclusive - startInclusive + 1)) - 1;
		return (value & ~(mask << startInclusive)) | ((bits & mask) << startInclusive);
	}
}
