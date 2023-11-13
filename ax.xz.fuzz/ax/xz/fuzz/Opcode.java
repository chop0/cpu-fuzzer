package ax.xz.fuzz;

import ax.xz.fuzz.parse.OperandLexer;
import ax.xz.fuzz.parse.OperandParser;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.OpKind;
import com.github.icedland.iced.x86.enc.Encoder;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.util.*;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Stream.concat;

public record Opcode(EnumSet<Prefix> prefixes, String mnemonic, int icedVariant, Operand[] operands) {
	public Opcode(EnumSet<Prefix> prefixes, String mnemonic, int icedVariant, Operand[] operands) {
		this.prefixes = prefixes;
		this.mnemonic = mnemonic;
		this.icedVariant = icedVariant;
		this.operands = operands;

		// guess the width of the immediate operands
		var insn = configureRandomly(new Random(0), ResourcePartition.all(true));

		var encoder = new Encoder(64, _ -> {
		});

		int[] explicitIndexMap = IntStream.range(0, operands.length).filter(n -> operands[n] instanceof Operand.Counted).toArray();
		for (; ; ) {
			try {
				encoder.encode(insn, 0);
				break;
			} catch (Exception e) {
				if (e.getMessage().contains("Expected OpKind")) { // todo: this is terribel
					int opKind = Integer.parseInt(e.getMessage().split("Expected OpKind: ")[1].split(",")[0]);
					int index = Integer.parseInt(e.getMessage().split("Operand ")[1].split(":")[0]);
					operands[explicitIndexMap[index]] = new Operand.Counted.Imm(((Operand.Counted.Imm) operands[explicitIndexMap[index]]).bitSize(), opKind);
					insn.setOpKind(index, opKind);
				} else {
					break;
				}
			}
		}
	}

	public static Opcode of(int icedVariant, String icedFieldName) {
		var parts = icedFieldName.split("_");

		var prefixNames = Arrays.stream(Prefix.values()).map(Prefix::toString).collect(Collectors.toSet());
		var prefixes = Arrays.stream(parts).takeWhile(prefixNames::contains).toList();
		var mnemonic = Arrays.stream(parts).dropWhile(prefixes::contains).findFirst().orElseThrow();


		var sops = InstructionReference.suppressedOperands(Instruction.create(icedVariant), mnemonic);
		if (sops == null)
			return null;

		var operands = concat(Arrays.stream(parts).dropWhile(prefixes::contains).skip(1)
						.map(part -> tryParseOperand(part, prefixes)).filter(Objects::nonNull),
				Arrays.stream(sops))
				.toArray(Operand[]::new);

		if (operands.length < Instruction.create(icedVariant).getOpCount())
			throw new IllegalStateException("Not enough operands");

		return new Opcode(
				prefixes.stream().map(Prefix::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(Prefix.class))),
				mnemonic,
				icedVariant,
				operands
		);
	}

	private static Operand tryParseOperand(String part, List<String> prefixes) {
		var lexer = new OperandLexer(CharStreams.fromString(part));
		lexer.removeErrorListeners();
		var parser = new OperandParser(new CommonTokenStream(lexer));
		parser.removeErrorListeners();

		var walker = new ParseTreeWalker();
		var listener = new OperandWalker(prefixes.contains("EVEX"));
		try {
			walker.walk(listener, parser.operand());
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		return listener.getOperand();
	}

	Instruction ofRandom(ResourcePartition resourcePartition, RandomGenerator randomGenerator) throws BlockGenerator.NoPossibilitiesException {
		var insn = Instruction.create(icedVariant);

		int explicitOpIdx = 0;
		for (var operand : operands) {
			switch (operand) {
				case Operand.Counted counted -> {
					counted.setRandom(randomGenerator, insn, explicitOpIdx++, resourcePartition);
					if (insn.getOpKind(explicitOpIdx - 1) == OpKind.REGISTER)
						if (RegisterSet.EXTENDED_GP.hasRegister(insn.getOpRegister(explicitOpIdx - 1)))
							resourcePartition = resourcePartition.withAllowedRegisters(resourcePartition.allowedRegisters().subtract(RegisterSet.LEGACY_HIGH_GP));
						else if (RegisterSet.LEGACY_HIGH_GP.hasRegister(insn.getOpRegister(explicitOpIdx - 1)))
							resourcePartition = resourcePartition.withAllowedRegisters(resourcePartition.allowedRegisters().subtract(RegisterSet.EXTENDED_GP));
				}
				case Operand.Uncounted uncounted -> uncounted.setRandom(randomGenerator, insn, resourcePartition);
				case Operand.SuppressedOperand _ -> {
				}
			}

		}

		return insn;
	}

	boolean fulfilledBy(boolean evex, ResourcePartition rp) {
		return (evex || !prefixes.contains(Prefix.EVEX)) && Arrays.stream(operands).allMatch(n -> n.fulfilledBy(rp));
	}

	public Instruction configureRandomly(RandomGenerator random, ResourcePartition rp) {
		var insn = Instruction.create(icedVariant);
		int explicitOpIdx = 0;

		try {
			for (var operand : operands) {
				switch (operand) {
					case Operand.Counted counted ->
							counted.setRandom(random, insn, explicitOpIdx++, rp);
					case Operand.Uncounted uncounted -> uncounted.setRandom(random, insn, rp);
					case Operand.SuppressedOperand _ -> {
					}
				}
			}
		} catch (BlockGenerator.NoPossibilitiesException e) {
			throw new RuntimeException(e);
		}

		return insn;
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

	enum Prefix {
		D3NOW,
		EVEX,
		VEX,
		XOP
	}
}
