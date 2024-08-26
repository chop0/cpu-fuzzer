package ax.xz.fuzz.x86.arch;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.*;
import ax.xz.fuzz.x86.parse.OperandLexer;
import ax.xz.fuzz.x86.parse.OperandParser;
import ax.xz.fuzz.x86.operand.Imm;
import ax.xz.fuzz.x86.operand.Operand;
import ax.xz.fuzz.x86.operand.OperandWalker;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.enc.Encoder;
import com.github.icedland.iced.x86.info.MandatoryPrefix;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.lang.foreign.MemorySegment;
import java.util.*;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Stream.concat;

@JsonInclude(NON_NULL)
public final class X86Opcode implements Opcode {
	private final String mnemonic;

	private final EnumSet<Prefix> prefixes;
	private final String icedFieldName;
	private final int icedVariant;
	private final Operand[] operands;

	@JsonCreator
	public X86Opcode(EnumSet<Prefix> prefixes, String icedFieldName, String mnemonic, int icedVariant, Operand[] operands) {
		this.mnemonic = mnemonic;
		this.prefixes = prefixes;
		this.icedFieldName = icedFieldName;
		this.icedVariant = icedVariant;
		this.operands = operands;

		// guess the width of the immediate operands
		var mem = MemorySegment.ofAddress(0x40000).reinterpret(0x1000);
		var anything = new ResourcePartition(StatusFlag.all(), X86Architecture.ofNative().validRegisters(), MemoryPartition.of(mem), mem);
		X86InstructionBuilder insn = null;
		try {
			insn = select(new Random(0), anything);
		} catch (NoPossibilitiesException e) {
			throw new RuntimeException(e);
		}

		var encoder = new Encoder(64, _ -> {
		});

		int[] explicitIndexMap = IntStream.range(0, operands.length).filter(n -> operands[n].counted()).toArray();
		for (; ; ) {
			try {
				encoder.encode(insn.instruction(), 0);
				break;
			} catch (Exception e) {
				if (e.getMessage().contains("Expected OpKind")) { // todo: this is terribel
					int opKind = Integer.parseInt(e.getMessage().split("Expected OpKind: ")[1].split(",")[0]);
					int index = Integer.parseInt(e.getMessage().split("Operand ")[1].split(":")[0]);
					operands[explicitIndexMap[index]] = new Imm(((Imm) operands[explicitIndexMap[index]]).bitSize(), opKind);
					insn.instruction().setOpKind(index, opKind);
				} else {
					break;
				}
			}
		}
	}

	@Override
	public X86InstructionBuilder select(RandomGenerator rng, ResourcePartition resourcePartition) throws NoPossibilitiesException {
		var insn = Instruction.create(icedVariant);
		var builder = new X86InstructionBuilder(insn);

		int explicitOpIdx = 0;
		for (var operand : operands) {
			int operandIndex = -1;
			if (operand.counted()) {
				operandIndex = explicitOpIdx++;
			}

			operand.select(rng, builder.instruction(), operandIndex, resourcePartition);
		}

		var opcode = insn.getOpCode();

		if (opcode.getMandatoryPrefix() != MandatoryPrefix.PNP) {

//			if (!opcode.getNFx() && !mnemonic.toLowerCase().startsWith("cvt")) {
//				insn.setRepePrefix(rng.nextInt(30) == 0);
//				insn.setRepnePrefix(rng.nextInt(30) == 0);
//				insn.setRepPrefix(rng.nextInt(30) == 0);
//			}
//			insn.setLockPrefix(rng.nextInt(30) == 0);

//			if (rng.nextInt(3) == 0) {
//				var intersection = SEGMENT.intersection(resourcePartition.allowedRegisters());
//				if (!intersection.isEmpty())
//					insn.setSegmentPrefix(((x86RegisterDescriptor)intersection.select(rng)).icedId());
//			}
		}

//		if ( rng.nextBoolean()) {
//			switch (rng.nextInt(3)) {
//				case 0 -> insn.setRepnePrefix(true);
//				case 1 -> insn.setRepePrefix(true);
//				case 2 -> insn.setRepPrefix(true);
//			}
//		}

		return new X86InstructionBuilder(insn);
	}

	@Override
	public boolean fulfilledBy(ResourcePartition rp) {
		for (Operand n : operands) {
			if (!n.fulfilledBy(rp)) {
				return false;
			}
		}
		return true;
	}

	public EnumSet<Prefix> prefixes() {
		return prefixes;
	}

	public String icedFieldName() {
		return icedFieldName;
	}

	public int icedVariant() {
		return icedVariant;
	}

	public Operand[] operands() {
		return operands;
	}

	@Override
	public int hashCode() {
		return Objects.hash(prefixes, icedFieldName, mnemonic, icedVariant, operands);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (X86Opcode) obj;
		return Objects.equals(this.prefixes, that.prefixes) &&
		       Objects.equals(this.icedFieldName, that.icedFieldName) &&
		       Objects.equals(this.mnemonic, that.mnemonic) &&
		       this.icedVariant == that.icedVariant &&
		       Objects.equals(this.operands, that.operands);
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();
		for (var prefix : prefixes) {
			sb.append(prefix.toString()).append(".");
		}
		sb.append(mnemonic);
		for (Operand operand : operands) {
			sb.append(" ");
			sb.append(operand);
		}

		return sb.toString();
	}

	public enum Prefix {
		D3NOW,
		EVEX,
		VEX,
		XOP
	}

	public static X86Opcode of(int icedVariant, String icedFieldName) {
		var parts = icedFieldName.split("_");

		var prefixNames = Arrays.stream(Prefix.values()).map(Prefix::toString).collect(Collectors.toSet());
		var prefixes = Arrays.stream(parts).takeWhile(prefixNames::contains).toList();
		var mnemonic = Arrays.stream(parts).dropWhile(prefixes::contains).findFirst().orElseThrow();

		var sops = InstructionReference.suppressedOperands(Instruction.create(icedVariant), mnemonic);
		if (sops == null)
			return null;

		var operands = concat(Arrays.stream(parts).dropWhile(prefixes::contains).skip(1)
				.map(part -> tryParseOperand(part, prefixes)).filter(Objects::nonNull)
				.flatMap(List::stream),
			Arrays.stream(sops))
			.toArray(Operand[]::new);

		var fakeMem = MemorySegment.ofAddress(0x4000).reinterpret(0x1000);
		var supportedSet = new ResourcePartition(StatusFlag.all(), X86Architecture.ofNative().validRegisters(), MemoryPartition.of(fakeMem), fakeMem);
		if (!Arrays.stream(operands).allMatch(n -> n.fulfilledBy(supportedSet)))
			return null;

		if (operands.length < Instruction.create(icedVariant).getOpCount())
			return null;

		return new X86Opcode(
			prefixes.stream().map(Prefix::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(Prefix.class))),
			icedFieldName,
			mnemonic,
			icedVariant,
			requireNonNull(operands)
		);
	}

	private static List<Operand> tryParseOperand(String part, List<String> prefixes) {
		var lexer = new OperandLexer(CharStreams.fromString(part));
		lexer.removeErrorListeners();
		var parser = new OperandParser(new CommonTokenStream(lexer));
		parser.removeErrorListeners();

		var walker = new ParseTreeWalker();
		var listener = new OperandWalker(prefixes.contains("EVEX"));
		try {
			walker.walk(listener, parser.operand());
		} catch (Exception e) {
			return null;
		}

		return listener.getOperands();
	}

	@Override
	public String mnemonic() {
		return mnemonic;
	}
}
