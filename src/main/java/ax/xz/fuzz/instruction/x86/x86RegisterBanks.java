package ax.xz.fuzz.instruction.x86;

import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.instruction.RegisterSet;

import static ax.xz.fuzz.instruction.x86.x86RegisterBank.*;
import static ax.xz.fuzz.instruction.x86.x86RegisterDescriptor.*;


import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class x86RegisterBanks {
	public static final RegisterSet SPECIAL = RegisterSet.of(MXCSR);
	public static final List<RegisterSet> banks = Stream.of(new RegisterDescriptor[][]{
		{AL, AH, AX, EAX, RAX},
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
	}).map(n -> RegisterSet.of(n)).toList();

	public static final RegisterSet[] bankSets = banks.stream()
		.toArray(RegisterSet[]::new);

	public static RegisterSet LEGACY_HIGH_GP = withBanks(GPRH);
	public static RegisterSet GPB = withBanks(GPRL, GPRH);
	public static RegisterSet GPW = withBanks(GPRW);
	public static RegisterSet GPD = withBanks(GPRD);
	public static RegisterSet GPQ = withBanks(GPRQ);
	public static RegisterSet EXTENDED_GP = GPQ // things that trigger rex prefix
		.union(rangeClosed(R8D, R15D))
		.union(rangeClosed(R8W, R15W))
		.union(rangeClosed(R8L, R15L))
		.union(RegisterSet.of(SIL, DIL, SPL, BPL));
	public static RegisterSet GP = GPB.union(GPW).union(GPD).union(GPQ);
	public static RegisterSet CR = rangeClosed(CR0, CR15);
	public static RegisterSet MM = rangeClosed(MM0, MM7);
	public static RegisterSet XMM_AVX2 = rangeClosed(XMM0, XMM15);
	public static RegisterSet XMM_AVX512 = rangeClosed(XMM0, XMM31);
	public static RegisterSet YMM_AVX2 = rangeClosed(YMM0, YMM15);
	public static RegisterSet VECTOR_VEX = MM.union(XMM_AVX2).union(YMM_AVX2);
	public static RegisterSet YMM_AVX512 = rangeClosed(YMM0, YMM31);
	public static RegisterSet ZMM_VEX = rangeClosed(ZMM0, ZMM15);
	public static RegisterSet ZMM_AVX512 = rangeClosed(ZMM0, ZMM31);
	public static RegisterSet VECTOR_EVEX = MM.union(XMM_AVX512).union(YMM_AVX512).union(ZMM_AVX512);
	public static RegisterSet TMM = rangeClosed(TMM0, TMM7);
	public static RegisterSet ST = rangeClosed(ST0, ST7);
	public static RegisterSet MASK = rangeClosed(K0, K7);
	public static RegisterSet SEGMENT = rangeClosed(ES, GS);

	public static RegisterSet ALL_AVX512 = GP.union(ST).union(VECTOR_EVEX).union(MASK).union(SEGMENT).union(SPECIAL).union(CR);
	public static RegisterSet ALL_AVX2 = GP.union(VECTOR_VEX).union(MASK).union(SEGMENT).union(SPECIAL).union(CR);

	public static RegisterSet vector(int size, boolean hasEvexPrefix) {
		return switch (size) {
			case 64 -> MM;
			case 128 -> hasEvexPrefix ? XMM_AVX512 : XMM_AVX2;
			case 256 -> hasEvexPrefix ? YMM_AVX512 : YMM_AVX2;
			case 512 -> hasEvexPrefix ? ZMM_AVX512 : ZMM_VEX;
			default -> throw new IllegalStateException("Unexpected operand size: " + size);
		};
	}

	public static RegisterSet getAssociatedRegisters(RegisterDescriptor register) {
		for (var bank : banks) {
			if (bank.hasRegister(register))
				return bank;
		}

		return RegisterSet.of(register);
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

	private static RegisterSet withBanks(x86RegisterBank... bank) {
		var bankSet = EnumSet.of(bank[0], bank);
		return Arrays.stream(x86RegisterDescriptor.values()).filter(n -> bankSet.contains(n.bank())).collect(RegisterSet.collector());
	}

	private static RegisterSet rangeClosed(x86RegisterDescriptor start, x86RegisterDescriptor end) {
		return IntStream.rangeClosed(start.ordinal(), end.ordinal())
			.mapToObj(n -> x86RegisterDescriptor.values()[n]).collect(RegisterSet.collector());
	}
}
