package ax.xz.fuzz.riscv;

import ax.xz.fuzz.riscv.base.RiscvBaseRegister;

import java.nio.ByteBuffer;

import static ax.xz.fuzz.riscv.base.RiscvBaseOpcode.ArithmeticImmOpcode.*;
import static ax.xz.fuzz.riscv.base.RiscvBaseOpcode.ArithmeticOpcode.*;
import static ax.xz.fuzz.riscv.base.RiscvBaseOpcode.BranchOpcode.*;
import static ax.xz.fuzz.riscv.base.RiscvBaseOpcode.LoadOpcode.*;
import static ax.xz.fuzz.riscv.base.RiscvBaseRegisters.x0;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

public final class RiscvAssembler extends RiscvAssemblerBase {

	public RiscvAssembler(long pc, RiscvArchitecture architecture) {
		super(architecture, pc);
	}

	public void add(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2) {
		arithmetic(ADD, rd, rs1, rs2);
	}

	public void sub(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2) {
		arithmetic(SUB, rd, rs1, rs2);
	}

	public void xor(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2) {
		arithmetic(XOR, rd, rs1, rs2);
	}

	public void or(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2) {
		arithmetic(OR, rd, rs1, rs2);
	}

	public void and(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2) {
		arithmetic(AND, rd, rs1, rs2);
	}

	public void sll(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2) {
		arithmetic(SLL, rd, rs1, rs2);
	}

	public void srl(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2) {
		arithmetic(SRL, rd, rs1, rs2);
	}

	public void sra(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2) {
		arithmetic(SRA, rd, rs1, rs2);
	}

	public void slt(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2) {
		arithmetic(SLT, rd, rs1, rs2);
	}

	public void sltu(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2) {
		arithmetic(SLTU, rd, rs1, rs2);
	}

	public void addi(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		arithmetic(ADDI, rd, rs1, imm);
	}

	public void xori(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		arithmetic(XORI, rd, rs1, imm);
	}

	public void ori(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		arithmetic(ORI, rd, rs1, imm);
	}

	public void andi(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		arithmetic(ANDI, rd, rs1, imm);
	}

	public void slli(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		arithmetic(SLLI, rd, rs1, imm);
	}

	public void srli(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		arithmetic(SRLI, rd, rs1, imm);
	}

	public void srai(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		arithmetic(SRAI, rd, rs1, imm);
	}

	public void slti(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		arithmetic(SLTI, rd, rs1, imm);
	}

	public void sltiu(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		arithmetic(SLTIU, rd, rs1, imm);
	}

	public void lb(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		load(LB, rd, rs1, imm);
	}

	public void lh(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		load(LH, rd, rs1, imm);
	}

	public void lw(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		load(LW, rd, rs1, imm);
	}

	public void lbu(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		load(LBU, rd, rs1, imm);
	}

	public void lhu(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		load(LHU, rd, rs1, imm);
	}

	public void lb(RiscvBaseRegister.Gpr rd, Label location) {
		load(LB, rd, location);
	}

	public void lh(RiscvBaseRegister.Gpr rd, Label location) {
		load(LH, rd, location);
	}

	public void lw(RiscvBaseRegister.Gpr rd, Label location) {
		load(LW, rd, location);
	}

	public void lbu(RiscvBaseRegister.Gpr rd, Label location) {
		load(LBU, rd, location);
	}

	public void lhu(RiscvBaseRegister.Gpr rd, Label location) {
		load(LHU, rd, location);
	}

	public void beq(RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2, Label location) {
		branch(BEQ, rs1, rs2, location);
	}

	public void bne(RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2, Label location) {
		branch(BNE, rs1, rs2, location);
	}

	public void blt(RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2, Label location) {
		branch(BLT, rs1, rs2, location);
	}

	public void bltu(RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2, Label location) {
		branch(BLTU, rs1, rs2, location);
	}

	public void bge(RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2, Label location) {
		branch(BGE, rs1, rs2, location);
	}

	public void bgeu(RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2, Label location) {
		branch(BGEU, rs1, rs2, location);
	}

	public void j(Label target) {
		jal(x0, target);
	}

	public void la(RiscvBaseRegister.Gpr rd, Label location) {
		loadAddress(rd, location);
	}

	public void db(byte b) {
		data(new byte[]{b});
	}

	public void db(byte... bytes) {
		data(bytes);
	}

	public void dw(short s) {
		data(ByteBuffer.allocate(2).order(LITTLE_ENDIAN).putShort(s).array());
	}

	public void dd(int i) {
		data(ByteBuffer.allocate(4).order(LITTLE_ENDIAN).putInt(i).array());
	}

	public void dq(long l) {
		data(ByteBuffer.allocate(8).order(LITTLE_ENDIAN).putLong(l).array());
	}
}
