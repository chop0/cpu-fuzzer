package ax.xz.fuzz;

import ax.xz.fuzz.parse.OperandBaseListener;
import ax.xz.fuzz.parse.OperandParser;
import com.github.icedland.iced.x86.OpKind;

import static ax.xz.fuzz.Operand.Counted.*;
import static ax.xz.fuzz.Operand.Uncounted.*;
import static java.lang.Integer.parseInt;

public class OperandWalker extends OperandBaseListener {
	private final boolean hasEvexPrefix;

	private Operand operand;

	public OperandWalker(boolean hasEvexPrefix) {
		this.hasEvexPrefix = hasEvexPrefix;
	}

	public Operand getOperand() {
		return operand;
	}

	@Override
	public void enterFixedReg(OperandParser.FixedRegContext ctx) {
		operand = (new FixedReg(Registers.byName(ctx.getText())));
	}

	@Override
	public void enterImm(OperandParser.ImmContext ctx) {
		var width = parseInt(ctx.DIGITS().getText());
		operand = new Imm(width, switch (width) {
			case 4, 8 -> OpKind.IMMEDIATE8;
			case 16 -> OpKind.IMMEDIATE16;
			case 32 -> OpKind.IMMEDIATE32;
			case 64 -> OpKind.IMMEDIATE64;
			default -> throw new IllegalStateException("Unexpected operand size: " + width);
		});
	}

	@Override
	public void enterImplicitNumber(OperandParser.ImplicitNumberContext ctx) {
		operand = (new FixedNumber(Byte.parseByte(ctx.DIGITS().getText())));
	}

	@Override
	public void enterMask(OperandParser.MaskContext ctx) {
		operand = (new Mask(ctx.Z() != null));
	}

	@Override
	public void enterMem(OperandParser.MemContext ctx) {
		operand = (new Mem(ctx.MEMORY_SIZE() != null ? Integer.parseInt(ctx.MEMORY_SIZE().getText().substring(1)) : 0));
	}

	@Override
	public void enterMoffs(OperandParser.MoffsContext ctx) {
		operand = (new Moffs(parseInt(ctx.DIGITS().getText())));
	}

	@Override
	public void enterReg(OperandParser.RegContext ctx) {
		var registerCandidates = RegisterSet.generalPurpose(parseInt(ctx.DIGITS() != null ? ctx.DIGITS().getText() : ctx.MEMORY_SIZE().getText().substring(1)));

		if (ctx.MEMORY_SIZE() == null)
			operand = new Reg(registerCandidates);
		else
			operand = new RegOrMem(registerCandidates, Integer.parseInt(ctx.MEMORY_SIZE().getText().substring(1)));
	}

	@Override
	public void enterSaeControl(OperandParser.SaeControlContext ctx) {
		operand = (new SaeControl());
	}

	@Override
	public void enterTileStride(OperandParser.TileStrideContext ctx) {
		operand = (new TileStride());
	}

	@Override
	public void enterVectorOperand(OperandParser.VectorOperandContext ctx) {
		var bank = getRegisterBank(ctx.VECTOR_BANK().getText());

		boolean hasMemorySpec = ctx.MEMORY_SIZE() != null;
		boolean hasMultireg = ctx.MULTIREG_COUNT() != null;
		assert !(hasMemorySpec && hasMultireg);

		if (hasMultireg) {
			int multiregCount = parseInt(ctx.MULTIREG_COUNT().getText().substring(1));

			var candidates = bank.stream()
					.filter(r -> r >= bank.first() && (r - bank.first()) % multiregCount == 0)
					.boxed()
					.collect(RegisterSet.collector());

			operand = new VectorMultireg(multiregCount, candidates);
		} else if (hasMemorySpec) {
			var memorySize = parseInt(ctx.MEMORY_SIZE().getText().substring(1));

			if (ctx.BROADCAST_SIZE() == null)
				operand = (new RegOrMem(bank, memorySize));
			else
				operand = (new RegOrMemBroadcastable(bank, memorySize, parseInt(ctx.BROADCAST_SIZE().getText().substring(1))));
		} else {
			operand = (new Reg(bank));
		}
	}

	@Override
	public void enterEmbeddedRC(OperandParser.EmbeddedRCContext ctx) {
		operand = (new EmbeddedRoundingControl());
	}

	@Override
	public void enterVsib(OperandParser.VsibContext ctx) {
		operand = new VSIB(Integer.parseInt(ctx.MEMORY_SIZE().getText().substring(1)), getRegisterBank(ctx.vector_bank_short().getText()));
	}

	private RegisterSet getRegisterBank(String bankName) {
		return switch (bankName) {
			case "MM" -> RegisterSet.vector(64, hasEvexPrefix);
			case "X", "XMM" -> RegisterSet.vector(128, hasEvexPrefix);
			case "Y", "YMM" -> RegisterSet.vector(256, hasEvexPrefix);
			case "Z", "ZMM" -> RegisterSet.vector(512, hasEvexPrefix);
			case "TMM" -> RegisterSet.TMM;
			case "STI" -> RegisterSet.ST;
			case "SREG" -> RegisterSet.SEGMENT;
			case "K", "KR" -> RegisterSet.MASK;
			default -> throw new IllegalStateException("Unexpected operand bank: " + bankName);
		};
	}
}
