package ax.xz.fuzz.instruction.x86;

import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.instruction.RegisterSet;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.github.icedland.iced.x86.Register;

import java.util.HashMap;

import static ax.xz.fuzz.instruction.x86.x86RegisterBank.*;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;

public enum x86RegisterDescriptor implements RegisterDescriptor {
	AL(Register.AL),
	CL(Register.CL),
	DL(Register.DL),
	BL(Register.BL),
	AH(Register.AH),
	CH(Register.CH),
	DH(Register.DH),
	BH(Register.BH),
	SPL(Register.SPL),
	BPL(Register.BPL),
	SIL(Register.SIL),
	DIL(Register.DIL),
	R8L(Register.R8L),
	R9L(Register.R9L),
	R10L(Register.R10L),
	R11L(Register.R11L),
	R12L(Register.R12L),
	R13L(Register.R13L),
	R14L(Register.R14L),
	R15L(Register.R15L),
	AX(Register.AX),
	CX(Register.CX),
	DX(Register.DX),
	BX(Register.BX),
	SP(Register.SP),
	BP(Register.BP),
	SI(Register.SI),
	DI(Register.DI),
	R8W(Register.R8W),
	R9W(Register.R9W),
	R10W(Register.R10W),
	R11W(Register.R11W),
	R12W(Register.R12W),
	R13W(Register.R13W),
	R14W(Register.R14W),
	R15W(Register.R15W),
	EAX(Register.EAX),
	ECX(Register.ECX),
	EDX(Register.EDX),
	EBX(Register.EBX),
	ESP(Register.ESP),
	EBP(Register.EBP),
	ESI(Register.ESI),
	EDI(Register.EDI),
	R8D(Register.R8D),
	R9D(Register.R9D),
	R10D(Register.R10D),
	R11D(Register.R11D),
	R12D(Register.R12D),
	R13D(Register.R13D),
	R14D(Register.R14D),
	R15D(Register.R15D),
	RAX(Register.RAX),
	RCX(Register.RCX),
	RDX(Register.RDX),
	RBX(Register.RBX),
	RSP(Register.RSP),
	RBP(Register.RBP),
	RSI(Register.RSI),
	RDI(Register.RDI),
	R8(Register.R8),
	R9(Register.R9),
	R10(Register.R10),
	R11(Register.R11),
	R12(Register.R12),
	R13(Register.R13),
	R14(Register.R14),
	R15(Register.R15),
	EIP(Register.EIP),
	RIP(Register.RIP),
	ES(Register.ES),
	CS(Register.CS),
	SS(Register.SS),
	DS(Register.DS),
	FS(Register.FS, "FSBASE"),
	GS(Register.GS, "GSBASE"),
	XMM0(Register.XMM0),
	XMM1(Register.XMM1),
	XMM2(Register.XMM2),
	XMM3(Register.XMM3),
	XMM4(Register.XMM4),
	XMM5(Register.XMM5),
	XMM6(Register.XMM6),
	XMM7(Register.XMM7),
	XMM8(Register.XMM8),
	XMM9(Register.XMM9),
	XMM10(Register.XMM10),
	XMM11(Register.XMM11),
	XMM12(Register.XMM12),
	XMM13(Register.XMM13),
	XMM14(Register.XMM14),
	XMM15(Register.XMM15),
	XMM16(Register.XMM16),
	XMM17(Register.XMM17),
	XMM18(Register.XMM18),
	XMM19(Register.XMM19),
	XMM20(Register.XMM20),
	XMM21(Register.XMM21),
	XMM22(Register.XMM22),
	XMM23(Register.XMM23),
	XMM24(Register.XMM24),
	XMM25(Register.XMM25),
	XMM26(Register.XMM26),
	XMM27(Register.XMM27),
	XMM28(Register.XMM28),
	XMM29(Register.XMM29),
	XMM30(Register.XMM30),
	XMM31(Register.XMM31),
	YMM0(Register.YMM0),
	YMM1(Register.YMM1),
	YMM2(Register.YMM2),
	YMM3(Register.YMM3),
	YMM4(Register.YMM4),
	YMM5(Register.YMM5),
	YMM6(Register.YMM6),
	YMM7(Register.YMM7),
	YMM8(Register.YMM8),
	YMM9(Register.YMM9),
	YMM10(Register.YMM10),
	YMM11(Register.YMM11),
	YMM12(Register.YMM12),
	YMM13(Register.YMM13),
	YMM14(Register.YMM14),
	YMM15(Register.YMM15),
	YMM16(Register.YMM16),
	YMM17(Register.YMM17),
	YMM18(Register.YMM18),
	YMM19(Register.YMM19),
	YMM20(Register.YMM20),
	YMM21(Register.YMM21),
	YMM22(Register.YMM22),
	YMM23(Register.YMM23),
	YMM24(Register.YMM24),
	YMM25(Register.YMM25),
	YMM26(Register.YMM26),
	YMM27(Register.YMM27),
	YMM28(Register.YMM28),
	YMM29(Register.YMM29),
	YMM30(Register.YMM30),
	YMM31(Register.YMM31),
	ZMM0(Register.ZMM0),
	ZMM1(Register.ZMM1),
	ZMM2(Register.ZMM2),
	ZMM3(Register.ZMM3),
	ZMM4(Register.ZMM4),
	ZMM5(Register.ZMM5),
	ZMM6(Register.ZMM6),
	ZMM7(Register.ZMM7),
	ZMM8(Register.ZMM8),
	ZMM9(Register.ZMM9),
	ZMM10(Register.ZMM10),
	ZMM11(Register.ZMM11),
	ZMM12(Register.ZMM12),
	ZMM13(Register.ZMM13),
	ZMM14(Register.ZMM14),
	ZMM15(Register.ZMM15),
	ZMM16(Register.ZMM16),
	ZMM17(Register.ZMM17),
	ZMM18(Register.ZMM18),
	ZMM19(Register.ZMM19),
	ZMM20(Register.ZMM20),
	ZMM21(Register.ZMM21),
	ZMM22(Register.ZMM22),
	ZMM23(Register.ZMM23),
	ZMM24(Register.ZMM24),
	ZMM25(Register.ZMM25),
	ZMM26(Register.ZMM26),
	ZMM27(Register.ZMM27),
	ZMM28(Register.ZMM28),
	ZMM29(Register.ZMM29),
	ZMM30(Register.ZMM30),
	ZMM31(Register.ZMM31),
	K0(Register.K0),
	K1(Register.K1),
	K2(Register.K2),
	K3(Register.K3),
	K4(Register.K4),
	K5(Register.K5),
	K6(Register.K6),
	K7(Register.K7),
	BND0(Register.BND0),
	BND1(Register.BND1),
	BND2(Register.BND2),
	BND3(Register.BND3),
	CR0(Register.CR0),
	CR1(Register.CR1),
	CR2(Register.CR2),
	CR3(Register.CR3),
	CR4(Register.CR4),
	CR5(Register.CR5),
	CR6(Register.CR6),
	CR7(Register.CR7),
	CR8(Register.CR8),
	CR9(Register.CR9),
	CR10(Register.CR10),
	CR11(Register.CR11),
	CR12(Register.CR12),
	CR13(Register.CR13),
	CR14(Register.CR14),
	CR15(Register.CR15),
	DR0(Register.DR0),
	DR1(Register.DR1),
	DR2(Register.DR2),
	DR3(Register.DR3),
	DR4(Register.DR4),
	DR5(Register.DR5),
	DR6(Register.DR6),
	DR7(Register.DR7),
	DR8(Register.DR8),
	DR9(Register.DR9),
	DR10(Register.DR10),
	DR11(Register.DR11),
	DR12(Register.DR12),
	DR13(Register.DR13),
	DR14(Register.DR14),
	DR15(Register.DR15),
	ST0(Register.ST0, "ST(0)"),
	ST1(Register.ST1, "ST(1)"),
	ST2(Register.ST2, "ST(2)"),
	ST3(Register.ST3, "ST(3)"),
	ST4(Register.ST4, "ST(4)"),
	ST5(Register.ST5, "ST(5)"),
	ST6(Register.ST6, "ST(6)"),
	ST7(Register.ST7, "ST(7)"),
	MM0(Register.MM0),
	MM1(Register.MM1),
	MM2(Register.MM2),
	MM3(Register.MM3),
	MM4(Register.MM4),
	MM5(Register.MM5),
	MM6(Register.MM6),
	MM7(Register.MM7),
	TR0(Register.TR0),
	TR1(Register.TR1),
	TR2(Register.TR2),
	TR3(Register.TR3),
	TR4(Register.TR4),
	TR5(Register.TR5),
	TR6(Register.TR6),
	TR7(Register.TR7),
	TMM0(Register.TMM0),
	TMM1(Register.TMM1),
	TMM2(Register.TMM2),
	TMM3(Register.TMM3),
	TMM4(Register.TMM4),
	TMM5(Register.TMM5),
	TMM6(Register.TMM6),
	TMM7(Register.TMM7),
	MXCSR(-1),
	GDTR(-1),
	LDTR(-1),
	IDTR(-1),
	TR(-1),
	MSRS(-1),
	TSC(-1),
	SSP(-1),
	TSCAUX(-1),
	X87CONTROL(-1),
	X87STATUS(-1),
	X87TAG(-1),
	X87PUSH(-1),
	X87POP(-1),
	X87POP2(-1),
	UIF(-1),
	XCR0(-1),
	RFLAGS(-1);

	private static HashMap<String, x86RegisterDescriptor> registerMap;
	private static HashMap<Integer, x86RegisterDescriptor> icedIdMap;

	private final String[] aliases;
	private final x86RegisterBank bank;
	private final int indexWithinBank;
	private final int icedId;

	x86RegisterDescriptor(int icedId, String... aliases) {
		this.aliases = aliases;
		this.icedId = icedId;
		this.bank = findBank(icedId);
		this.indexWithinBank = switch (bank) {
			case GPRH -> icedId - Register.AH;
			case SPECIAL -> -1;
			case UPPER_XMM, UPPER_YMM, UPPER_ZMM -> Register.getNumber(icedId) - 16;
			default -> Register.getNumber(icedId);
		};

		addToMap(this);
	}

	@Override
	public int index() {
		return ordinal();
	}

	@Override
	public int widthBytes() {
		return switch (bank) {
			case SPECIAL -> 4;
			default -> Register.getSize(icedId);
		};
	}

	@Override
	public RegisterSet related() {
		return x86RegisterBanks.getAssociatedRegisters(this);
	}

	public int icedId() {
		return icedId;
	}

	public x86RegisterBank bank() {
		return bank;
	}

	public int indexWithinBank() {
		return indexWithinBank;
	}

	public boolean requiresEvex() {
		return switch (bank) {
			case UPPER_XMM, UPPER_YMM, LOWER_ZMM, UPPER_ZMM -> true;
			default -> false;
		};
	}

	private static void addToMap(x86RegisterDescriptor result) {
		if (registerMap == null)
			registerMap = new HashMap<>();
		if (icedIdMap == null)
			icedIdMap = new HashMap<>();

		if (registerMap.put(result.name(), result) != null)
			throw new IllegalArgumentException("Duplicate register name: " + result.name());
		for (var alias : result.aliases) {
			if (registerMap.put(alias, result) != null)
				throw new IllegalArgumentException("Duplicate register alias: " + alias);
		}

		if (result.icedId != -1 && icedIdMap.put(result.icedId, result) != null)
			throw new IllegalArgumentException("Duplicate iced ID: " + result.icedId);
	}

	@JsonCreator
	public static x86RegisterDescriptor fromString(String name) {
		return registerMap.get(name);
	}

	private static x86RegisterBank findBank(int icedID) {
		return switch ((Integer)icedID) {
			case Register.AH, Register.CH, Register.DH, Register.BH -> GPRH;
			case Integer i when Register.isGPR8(i) -> GPRL;
			case Integer i when Register.isGPR16(i) -> GPRW;
			case Integer i when Register.isGPR32(i) -> GPRD;
			case Integer i when Register.isGPR64(i) -> GPRQ;

			case Integer i when Register.isCR(i) -> CR;
			case Integer i when Register.isMM(i) -> MMX;
			case Integer i when Register.isTMM(i) -> TMM;
			case Integer i when Register.isK(i) -> MASK;
			case Integer i when Register.isSegmentRegister(i) -> SEGMENT;

			case Integer i when Register.isXMM(i) && Register.getNumber(i) < 16 -> LOWER_XMM;
			case Integer i when Register.isXMM(i) -> UPPER_XMM;

			case Integer i when Register.isYMM(i) && Register.getNumber(i) < 16 -> LOWER_YMM;
			case Integer i when Register.isYMM(i) -> UPPER_YMM;

			case Integer i when Register.isZMM(i) && Register.getNumber(i) < 16 -> LOWER_ZMM;
			case Integer i when Register.isZMM(i) -> UPPER_ZMM;

			default -> SPECIAL;
		};
	}

	public static RegisterDescriptor byIcedId(int firstOperand) {
		return icedIdMap.get(firstOperand);
	}
}
