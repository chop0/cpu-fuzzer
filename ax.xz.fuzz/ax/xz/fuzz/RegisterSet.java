package ax.xz.fuzz;


import com.github.icedland.iced.x86.Register;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ax.xz.fuzz.Registers.SPECIAL;
import static com.github.icedland.iced.x86.Register.*;
import static java.util.stream.IntStream.rangeClosed;

public final class RegisterSet {
	public static RegisterSet GPB = RegisterSet.of(rangeClosed(AL, R15L).toArray());
	public static RegisterSet GPW = RegisterSet.of(rangeClosed(AX, R15W).toArray());
	public static RegisterSet GPD = RegisterSet.of(rangeClosed(EAX, R15D).toArray());
	public static RegisterSet GPQ = RegisterSet.of(rangeClosed(RAX, R15).toArray());
	public static RegisterSet GP = GPB.union(GPW).union(GPD).union(GPQ);

	public static RegisterSet EXTENDED_GP = RegisterSet.of(rangeClosed(R8L, R15L).toArray()).union(RegisterSet.of(SIL, DIL, SPL, BPL));
	public static RegisterSet LEGACY_HIGH_GP = RegisterSet.of(AH, CH, DH, BH);

	public static RegisterSet MM = RegisterSet.of(rangeClosed(MM0, MM7).toArray());

	public static RegisterSet XMM_VEX = RegisterSet.of(rangeClosed(XMM0, XMM15).toArray());
	public static RegisterSet XMM_EVEX = RegisterSet.of(rangeClosed(XMM0, XMM31).toArray());

	public static RegisterSet YMM_VEX = RegisterSet.of(rangeClosed(YMM0, YMM15).toArray());
	public static RegisterSet YMM_EVEX = RegisterSet.of(rangeClosed(YMM0, YMM31).toArray());

	public static RegisterSet ZMM_VEX = RegisterSet.of(rangeClosed(ZMM0, ZMM15).toArray());
	public static RegisterSet ZMM_EVEX = RegisterSet.of(rangeClosed(ZMM0, ZMM31).toArray());

	public static RegisterSet VECTOR_VEX = MM.union(XMM_VEX).union(YMM_VEX).union(ZMM_VEX);
	public static RegisterSet VECTOR_EVEX = MM.union(XMM_EVEX).union(YMM_EVEX).union(ZMM_EVEX);


	public static RegisterSet TMM = RegisterSet.of(rangeClosed(TMM0, TMM7).toArray());

	public static RegisterSet ST = RegisterSet.of(rangeClosed(ST0, ST7).toArray());

	public static RegisterSet MASK = RegisterSet.of(rangeClosed(K0, K7).toArray());

	public static RegisterSet SEGMENT = RegisterSet.of(rangeClosed(ES, GS).toArray());
	public static RegisterSet ALL_VEX = GP.union(VECTOR_VEX).union(MASK).union(SEGMENT).union(SPECIAL);
	public static RegisterSet ALL_EVEX = GP.union(ST).union(VECTOR_EVEX).union(MASK).union(SEGMENT).union(SPECIAL);


	public static RegisterSet of(int... registers) {
		return new RegisterSet(Arrays.stream(registers).collect(BitSet::new, BitSet::set, BitSet::or));
	}

	public static RegisterSet generalPurpose(int size) {
		return switch (size) {
			case 8 -> GPB;
			case 16 -> GPW;
			case 32 -> GPD;
			case 64 -> GPQ;
			default -> throw new IllegalStateException("Unexpected operand size: " + size);
		};
	}

	public static RegisterSet vector(int size, boolean hasEvexPrefix) {
		return switch (size) {
			case 64 -> MM;
			case 128 -> hasEvexPrefix ? XMM_EVEX : XMM_VEX;
			case 256 -> hasEvexPrefix ? YMM_EVEX : YMM_VEX;
			case 512 -> hasEvexPrefix ? ZMM_EVEX : ZMM_VEX;
			default -> throw new IllegalStateException("Unexpected operand size: " + size);
		};
	}

	public static Collector<Integer, BitSet, RegisterSet> collector() {
		return Collector.of(BitSet::new, BitSet::set, (bs1, bs2) -> {
			bs1.or(bs2);
			return bs1;
		}, RegisterSet::new);
	}

	private final BitSet registers;

	public RegisterSet(BitSet registers) {
		this.registers = registers;
	}

	public boolean hasRegister(int register) {
		return registers.get(register);
	}

	public RegisterSet intersection(RegisterSet other) {
		var newSet = copyOf();
		newSet.registers.and(other.registers);

		return newSet;
	}

	public RegisterSet union(RegisterSet other) {
		var newSet = copyOf();
		newSet.registers.or(other.registers);

		return newSet;
	}

	private RegisterSet copyOf() {
		return new RegisterSet((BitSet) registers.clone());
	}

	public boolean isEmpty() {
		return registers.isEmpty();
	}

	public int choose(RandomGenerator randomGenerator) {
		if (isEmpty())
			throw new IllegalStateException("Cannot choose from empty register set");

		int index = randomGenerator.nextInt(registers.cardinality());
		return registers.stream().skip(index).findFirst().orElseThrow();
	}

	public RegisterSet consecutiveBlocks(int blockSize, RegisterSet startRegisters) {
		var vectorBlocks = startRegisters.registers.stream()
				.filter(startIndex -> this.registers.nextClearBit(startIndex) - startIndex >= blockSize)
				.collect(BitSet::new, BitSet::set, BitSet::or);

		return new RegisterSet(vectorBlocks);
	}

	public IntStream stream() {
		return registers.stream();
	}

	public int first() {
		return registers.nextSetBit(0);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (RegisterSet) obj;
		return Objects.equals(this.registers, that.registers);
	}

	@Override
	public int hashCode() {
		return registers.hashCode();
	}

	@Override
	public String toString() {
		return registers.stream()
				.mapToObj(Registers::byValue)
				.collect(Collectors.joining(", ", "{", "}"));
	}

	public RegisterSet subtract(RegisterSet rhs) {
		var newSet = copyOf();
		newSet.registers.andNot(rhs.registers);
		return newSet;
	}

	public static final int[][] banks = {{AL, AH, AX, EAX, RAX},
			{BL, BH, BX, EBX, RBX},
			{CL, CH, CX, ECX, RCX},
			{DL, DH, DX, EDX, RDX},
			{SIL, SI, ESI, RSI},
			{DIL, DI, EDI, RDI},
			{SPL, SP, ESP, RSP},
			{BPL, BP, EBP, RBP},
			{R8L, R8W, R8D, R8},
			{R9L, R9W, R9D, R9},
			{R10L, R10W, R10D, R10},
			{R11L, R11W, R11D, R11},
			{R12L, R12W, R12D, R12},
			{R13L, R13W, R13D, R13},
			{R14L, R14W, R14D, R14},
			{R15L, R15W, R15D, R15},

			{XMM0, YMM0, ZMM0},
			{XMM1, YMM1, ZMM1},
			{XMM2, YMM2, ZMM2},
			{XMM3, YMM3, ZMM3},
			{XMM4, YMM4, ZMM4},
			{XMM5, YMM5, ZMM5},
			{XMM6, YMM6, ZMM6},
			{XMM7, YMM7, ZMM7},
			{XMM8, YMM8, ZMM8},
			{XMM9, YMM9, ZMM9},
			{XMM10, YMM10, ZMM10},
			{XMM11, YMM11, ZMM11},
			{XMM12, YMM12, ZMM12},
			{XMM13, YMM13, ZMM13},
			{XMM14, YMM14, ZMM14},
			{XMM15, YMM15, ZMM15},
			{XMM16, YMM16, ZMM16},
			{XMM17, YMM17, ZMM17},
			{XMM18, YMM18, ZMM18},
			{XMM19, YMM19, ZMM19},
			{XMM20, YMM20, ZMM20},
			{XMM21, YMM21, ZMM21},
			{XMM22, YMM22, ZMM22},
			{XMM23, YMM23, ZMM23},
			{XMM24, YMM24, ZMM24},
			{XMM25, YMM25, ZMM25},
			{XMM26, YMM26, ZMM26},
			{XMM27, YMM27, ZMM27},
			{XMM28, YMM28, ZMM28},
			{XMM29, YMM29, ZMM29},
			{XMM30, YMM30, ZMM30},
			{XMM31, YMM31, ZMM31},

			{MM0}, {MM1}, {MM2}, {MM3}, {MM4}, {MM5}, {MM6}, {MM7}
	};


	public static int[] getAssociatedRegisters(int register) {
		for (int[] bank : banks) {
			for (int i = 0; i < bank.length; i++) {
				if (bank[i] == register) {
					return bank;
				}
			}
		}

		return new int[]{register};
	}
}
