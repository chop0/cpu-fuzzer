package ax.xz.fuzz.instruction;


import ax.xz.fuzz.blocks.randomisers.ReverseRandomGenerator;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.random.RandomGenerator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.icedland.iced.x86.Register.*;
import static java.util.stream.IntStream.rangeClosed;

public final class RegisterSet implements Iterable<Integer> {
	private static final int NUM_WORDS = 5;

	public static RegisterSet GPB = RegisterSet.of(rangeClosed(AL, R15L).toArray());
	public static RegisterSet GPW = RegisterSet.of(rangeClosed(AX, R15W).toArray());
	public static RegisterSet GPD = RegisterSet.of(rangeClosed(EAX , R15D).toArray());
	public static RegisterSet GPQ = RegisterSet.of(rangeClosed(RAX, R15).toArray());
	public static RegisterSet GP = GPB.union(GPW).union(GPD).union(GPQ);

	public static RegisterSet CR = RegisterSet.of(rangeClosed(CR0, CR15).toArray());

	public static RegisterSet EXTENDED_GP = GPQ // things that trigger rex prefix
			.union(RegisterSet.of(rangeClosed(R8D, R15D).toArray()))
			.union(RegisterSet.of(rangeClosed(R8W, R15W).toArray()))
			.union(RegisterSet.of(rangeClosed(R8L, R15L).toArray()))
			.union(RegisterSet.of(SIL, DIL, SPL, BPL));
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
	public static final RegisterSet SPECIAL = of(Registers.MXCSR);
	public static RegisterSet ALL_VEX = GP.union(VECTOR_VEX).union(MASK).union(SEGMENT).union(SPECIAL).union(CR);
	public static RegisterSet ALL_EVEX = GP.union(ST).union(VECTOR_EVEX).union(MASK).union(SEGMENT).union(SPECIAL).union(CR);


	public static RegisterSet of(int... registers) {
		return Arrays.stream(registers).boxed().collect(RegisterSet.collector());
	}

	public static RegisterSet ofRange(int startInclusive, int endInclusive) {
		return rangeClosed(startInclusive, endInclusive).boxed().collect(RegisterSet.collector());
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

	public static Collector<Integer, RegisterSet, RegisterSet> collector() {
		return Collector.of(RegisterSet::new, (rs, idx) -> rs.setInPlace(idx, true), RegisterSet::union);
	}

	private final long[] data;

	private RegisterSet(long[] data) {
		this.data = data;
	}

	public RegisterSet() {
		this(new long[NUM_WORDS]);
	}

	public void setInPlace(int register, boolean value) {
		register -= 1;

		int address = bitAddress(register);
		int index = bitIndex(register);
		if (value) {
			data[address] |= 1L << index;
		} else {
			data[address] &= ~(1L << index);
		}
	}

	private int bitIndex(int register) {
		return register & 0b111111;
	}

	private int bitAddress(int register) {
		return register >> 6;
	}

	public boolean hasRegister(int register) {
		register -= 1;

		int address = bitAddress(register);
		int index = bitIndex(register);
		return (data[address] & (1L << index)) != 0;
	}

	public RegisterSet intersection(RegisterSet other) {
		var result = new long[NUM_WORDS];
		for (int i = 0; i < NUM_WORDS; i++) {
			result[i] = data[i] & other.data[i];
		}

		return new RegisterSet(result);
	}

	public boolean intersects(RegisterSet other) {
		for (int i = 0; i < NUM_WORDS; i++) {
			if ((data[i] & other.data[i]) != 0) {
				return true;
			}
		}
		return false;
	}

	public RegisterSet union(RegisterSet other) {
		var result = new long[NUM_WORDS];
		for (int i = 0; i < NUM_WORDS; i++) {
			result[i] = data[i] | other.data[i];
		}

		return new RegisterSet(result);
	}

	public boolean isEmpty() {
		for (int i = 0; i < NUM_WORDS; i++) {
			if (data[i] != 0) {
				return false;
			}
		}

		return true;
	}

	private int nextSetBit(int startIndex) {
		int address = bitAddress(startIndex);
		int index = bitIndex(startIndex);

		long current = data[address] >> index;
		if (current != 0) {
			return startIndex + Long.numberOfTrailingZeros(current);
		}

		for (int i = address + 1; i < data.length; i++) {
			if (data[i] != 0) {
				return (i << 6) + Long.numberOfTrailingZeros(data[i]);
			}
		}

		return -1;
	}

	private int nextClearBit(int startIndex) {
		int address = bitAddress(startIndex);
		int index = bitIndex(startIndex);

		long current = ~data[address] >> index;
		if (current != 0) {
			return startIndex + Long.numberOfTrailingZeros(current);
		}

		for (int i = address + 1; i < data.length; i++) {
			if (data[i] != -1) {
				return (i << 2) + Long.numberOfTrailingZeros(~data[i]);
			}
		}

		return -1;
	}

	public int size() {
		int count = 0;
		for (int i = 0; i < NUM_WORDS; i++) {
			count += Long.bitCount(data[i]);
		}
		return count;
	}

	public int select(RandomGenerator randomGenerator) {
		if (isEmpty())
			throw new IllegalStateException("Cannot choose from empty register set");

		int currentIndex = nextSetBit(0);
		int bound = randomGenerator.nextInt(size()) - 1;

		for (int i = 0; i < bound; i++) {
			currentIndex = nextSetBit(currentIndex + 1);
		}

		return currentIndex + 1;
	}

	public void reverse(ReverseRandomGenerator random, int outcome) {
		outcome -= 1;

		int currentIndex = nextSetBit(0);
		int bound = 0;

		while (currentIndex != outcome) {
			currentIndex = nextSetBit(currentIndex + 1);
			bound++;
		}

		random.pushInt(bound);
	}

	public RegisterSet consecutiveBlocks(int blockSize, RegisterSet startRegisters) {

		return startRegisters.stream().map(i -> i - 1)
				.filter(startIndex -> this.nextClearBit(startIndex) - startIndex >= blockSize).boxed()
				.collect(collector());
	}

	public IntStream stream() {
		return IntStream.iterate(nextSetBit(0), i -> i != -1, i -> nextSetBit(i + 1 )).map(i -> i + 1);
	}

	public int first() {
		return nextSetBit(0) + 1;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (RegisterSet) obj;
		return Arrays.equals(this.data, that.data);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(data);
	}

	@Override
	public String toString() {
		return stream()
				.mapToObj(Registers::byValue)
				.collect(Collectors.joining(", ", "{", "}"));
	}

	public RegisterSet subtract(RegisterSet rhs) {
		var result = new long[NUM_WORDS];
		for (int i = 0; i < NUM_WORDS; i++) {
			result[i] = data[i] & ~rhs.data[i];
		}

		return new RegisterSet(result);
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

			{MM0, XMM0, YMM0, ZMM0},
			{MM1, XMM1, YMM1, ZMM1},
			{MM2, XMM2, YMM2, ZMM2},
			{MM3, XMM3, YMM3, ZMM3},
			{MM4, XMM4, YMM4, ZMM4},
			{MM5, XMM5, YMM5, ZMM5},
			{MM6, XMM6, YMM6, ZMM6},
			{MM7, XMM7, YMM7, ZMM7},
			{ XMM8, YMM8, ZMM8},
			{ XMM9, YMM9, ZMM9},
			{ XMM10, YMM10, ZMM10},
			{ XMM11, YMM11, ZMM11},
			{ XMM12, YMM12, ZMM12},
			{ XMM13, YMM13, ZMM13},
			{ XMM14, YMM14, ZMM14},
			{ XMM15, YMM15, ZMM15},
			{ XMM16, YMM16, ZMM16},
			{ XMM17, YMM17, ZMM17},
			{ XMM18, YMM18, ZMM18},
			{ XMM19, YMM19, ZMM19},
			{ XMM20, YMM20, ZMM20},
			{ XMM21, YMM21, ZMM21},
			{ XMM22, YMM22, ZMM22},
			{ XMM23, YMM23, ZMM23},
			{ XMM24, YMM24, ZMM24},
			{ XMM25, YMM25, ZMM25},
			{ XMM26, YMM26, ZMM26},
			{ XMM27, YMM27, ZMM27},
			{ XMM28, YMM28, ZMM28},
			{ XMM29, YMM29, ZMM29},
			{ XMM30, YMM30, ZMM30},
			{ XMM31, YMM31, ZMM31},
	};

	public static final RegisterSet[] bankSets = Arrays.stream(banks)
			.map(RegisterSet::of)
			.toArray(RegisterSet[]::new);


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

	public static RegisterSet getAssociatedRegisterSet(int register) {
		return RegisterSet.of(getAssociatedRegisters(register));
	}

	@Override
	public Iterator<Integer> iterator() {
		return stream().iterator();
	}
}
