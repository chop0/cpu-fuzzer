package ax.xz.fuzz.x86.operand;

import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.x86.parse.OperandBaseListener;
import ax.xz.fuzz.x86.parse.OperandParser;
import ax.xz.fuzz.x86.arch.x86RegisterBanks;
import ax.xz.fuzz.x86.arch.x86RegisterDescriptor;
import com.github.icedland.iced.x86.OpKind;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Integer.parseInt;

public class OperandWalker extends OperandBaseListener {
	private final boolean hasEvexPrefix;

	private final List<Operand> operands = new ArrayList<>();

	public OperandWalker(boolean hasEvexPrefix) {
		this.hasEvexPrefix = hasEvexPrefix;
	}

	public List<Operand> getOperands() {
		return operands;
	}

	@Override
	public void enterOperand(OperandParser.OperandContext ctx) {
		operands.clear();
	}

	@Override
	public void enterFixedReg(OperandParser.FixedRegContext ctx) {
		operands.add(RmOperand.register(x86RegisterDescriptor.fromString(ctx.getText())));
	}

	@Override
	public void enterImm(OperandParser.ImmContext ctx) {
		var width = parseInt(ctx.DIGITS().getText());
		operands.add(new Imm(width, switch (width) {
			case 4, 8 -> OpKind.IMMEDIATE8;
			case 16 -> OpKind.IMMEDIATE16;
			case 32 -> OpKind.IMMEDIATE32;
			case 64 -> OpKind.IMMEDIATE64;
			default -> throw new IllegalStateException("Unexpected operand size: " + width);
		}));
	}

	@Override
	public void enterImplicitNumber(OperandParser.ImplicitNumberContext ctx) {
		operands.add(new FixedNumber(Byte.parseByte(ctx.DIGITS().getText())));
	}

	@Override
	public void enterMask(OperandParser.MaskContext ctx) {
		operands.add(RmOperand.mask());
		if (ctx.Z() != null)
			operands.add(AncillaryFlags.zeroing());
	}

	@Override
	public void enterMem(OperandParser.MemContext ctx) {
		operands.add(RmOperand.memory(ctx.MEMORY_SIZE() != null ? Integer.parseInt(ctx.MEMORY_SIZE().getText().substring(1)) : 64 * 8));
	}

	@Override
	public void enterMoffs(OperandParser.MoffsContext ctx) {
		operands.add(new Moffs(parseInt(ctx.DIGITS().getText())));
	}

	@Override
	public void enterReg(OperandParser.RegContext ctx) {
		var registerCandidates = x86RegisterBanks.generalPurpose(parseInt(ctx.DIGITS() != null ? ctx.DIGITS().getText() : ctx.MEMORY_SIZE().getText().substring(1)));

		if (ctx.MEMORY_SIZE() == null)
			operands.add(RmOperand.register(registerCandidates));
		else
			operands.add(RmOperand.rm(registerCandidates, Integer.parseInt(ctx.MEMORY_SIZE().getText().substring(1))));
	}

	@Override
	public void enterSaeControl(OperandParser.SaeControlContext ctx) {
		operands.add(AncillaryFlags.sae());
	}

	@Override
	public void enterTileStride(OperandParser.TileStrideContext ctx) {
		operands.add(new TileStride());
	}

	@Override
	public void enterVectorOperand(OperandParser.VectorOperandContext ctx) {
		var bank = getRegisterBank(ctx.VECTOR_BANK().getText());

		boolean hasMemorySpec = ctx.MEMORY_SIZE() != null;
		boolean hasMultireg = ctx.MULTIREG_COUNT() != null;
		assert !(hasMemorySpec && hasMultireg);

		if (hasMultireg) {
			int multiregCount = parseInt(ctx.MULTIREG_COUNT().getText().substring(1));

			var bankFirst = (x86RegisterDescriptor)bank.first();
			var candidates = bank.stream()
				.map(r -> (x86RegisterDescriptor) r)
				.filter(r -> r.icedId() >= bankFirst.icedId() && (r.icedId() - bankFirst.icedId()) % multiregCount == 0)
				.collect(RegisterSet.collector());

			operands.add(new VectorMultireg(multiregCount, candidates));
		} else if (hasMemorySpec) {
			var memorySize = parseInt(ctx.MEMORY_SIZE().getText().substring(1));

			if (ctx.BROADCAST_SIZE() == null)
				operands.add(RmOperand.rm(bank, memorySize));
			else
				operands.add(RmOperand.rm(bank, memorySize, parseInt(ctx.BROADCAST_SIZE().getText().substring(1))));
		} else {
			operands.add(RmOperand.register(bank));
		}
	}

	@Override
	public void enterEmbeddedRC(OperandParser.EmbeddedRCContext ctx) {
		operands.add(AncillaryFlags.erc());
	}

	@Override
	public void enterVsib(OperandParser.VsibContext ctx) {
		operands.add(new VSIB(Integer.parseInt(ctx.MEMORY_SIZE().getText().substring(1)), getRegisterBank(ctx.vector_bank_short().getText())));
	}

	private RegisterSet getRegisterBank(String bankName) {
		return switch (bankName) {
			case "MM" -> x86RegisterBanks.vector(64, hasEvexPrefix);
			case "X", "XMM" -> x86RegisterBanks.vector(128, hasEvexPrefix);
			case "Y", "YMM" -> x86RegisterBanks.vector(256, hasEvexPrefix);
			case "Z", "ZMM" -> x86RegisterBanks.vector(512, hasEvexPrefix);
			case "TMM" -> x86RegisterBanks.TMM;
			case "STI" -> x86RegisterBanks.ST;
			case "SREG" -> x86RegisterBanks.SEGMENT;
			case "K", "KR" -> x86RegisterBanks.MASK;
			default -> throw new IllegalStateException("Unexpected operand bank: " + bankName);
		};
	}
}
