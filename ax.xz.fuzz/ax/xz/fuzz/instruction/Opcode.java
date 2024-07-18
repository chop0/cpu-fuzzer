package ax.xz.fuzz.instruction;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.blocks.randomisers.ReverseRandomGenerator;
import ax.xz.fuzz.parse.OperandLexer;
import ax.xz.fuzz.parse.OperandParser;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.EnumSetDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.ser.std.EnumSetSerializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
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

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Stream.concat;

@JsonInclude(NON_NULL)
public record Opcode(EnumSet<Prefix> prefixes, String icedFieldName, String mnemonic, int icedVariant, Operand[] operands) {
	@JsonCreator
	public Opcode(EnumSet<Prefix> prefixes, String icedFieldName, String mnemonic, int icedVariant, Operand[] operands) {
		this.prefixes = prefixes;
		this.icedFieldName = icedFieldName;
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

		if (!Arrays.stream(operands).allMatch(n -> n.fulfilledBy(ResourcePartition.all(true))))
			return null;

		if (operands.length < Instruction.create(icedVariant).getOpCount())
			return null;

		return new Opcode(
				prefixes.stream().map(Prefix::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(Prefix.class))),
				icedFieldName,
				mnemonic,
				icedVariant,
				requireNonNull(operands)
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
			return null;
		}

		return listener.getOperand();
	}

	public Instruction select(RandomGenerator rng, ResourcePartition resourcePartition) throws NoPossibilitiesException {
		var insn = Instruction.create(icedVariant);

		int explicitOpIdx = 0;
		for (var operand : operands) {
			switch (operand) {
				case Operand.Counted counted -> {
					counted.select(rng, insn, explicitOpIdx++, resourcePartition);
					if (insn.getOpKind(explicitOpIdx - 1) == OpKind.REGISTER)
						if (RegisterSet.EXTENDED_GP.hasRegister(insn.getOpRegister(explicitOpIdx - 1)))
							resourcePartition = resourcePartition.withAllowedRegisters(resourcePartition.allowedRegisters().subtract(RegisterSet.LEGACY_HIGH_GP));
						else if (RegisterSet.LEGACY_HIGH_GP.hasRegister(insn.getOpRegister(explicitOpIdx - 1)))
							resourcePartition = resourcePartition.withAllowedRegisters(resourcePartition.allowedRegisters().subtract(RegisterSet.EXTENDED_GP));
				}
				case Operand.Uncounted uncounted -> uncounted.select(rng, insn, resourcePartition);
				case Operand.SuppressedOperand _ -> {
				}
			}
		}

//		insn.setRepePrefix(rng.nextInt(30) == 0);
//		insn.setRepnePrefix(rng.nextInt(30) == 0);
//		insn.setRepPrefix(rng.nextInt(30) == 0);
//		insn.setLockPrefix(rng.nextInt(30) == 0);

		if (rng.nextInt(3) == 0) {
			insn.setSegmentPrefix(RegisterSet.SEGMENT.select(rng));
		}

//		if ( rng.nextBoolean()) {
//			switch (rng.nextInt(3)) {
//				case 0 -> insn.setRepnePrefix(true);
//				case 1 -> insn.setRepePrefix(true);
//				case 2 -> insn.setRepPrefix(true);
//			}
//		}

		return insn;
	}

	public void reverse(ReverseRandomGenerator rng, ResourcePartition resourcePartition, Instruction insn) throws NoPossibilitiesException {
		int explicitOpIdx = 0;
		for (var operand : operands) {
			switch (operand) {
				case Operand.Counted counted -> {
					counted.reverse(rng, insn, explicitOpIdx++, resourcePartition);
					if (insn.getOpKind(explicitOpIdx - 1) == OpKind.REGISTER)
						if (RegisterSet.EXTENDED_GP.hasRegister(insn.getOpRegister(explicitOpIdx - 1)))
							resourcePartition = resourcePartition.withAllowedRegisters(resourcePartition.allowedRegisters().subtract(RegisterSet.LEGACY_HIGH_GP));
						else if (RegisterSet.LEGACY_HIGH_GP.hasRegister(insn.getOpRegister(explicitOpIdx - 1)))
							resourcePartition = resourcePartition.withAllowedRegisters(resourcePartition.allowedRegisters().subtract(RegisterSet.EXTENDED_GP));
				}
				case Operand.Uncounted uncounted -> uncounted.reverse(rng, insn, resourcePartition);
				case Operand.SuppressedOperand _ -> {
				}
			}
		}

//		insn.setRepePrefix(rng.nextInt(30) == 0);
//		insn.setRepnePrefix(rng.nextInt(30) == 0);
//		insn.setRepPrefix(rng.nextInt(30) == 0);
//		insn.setLockPrefix(rng.nextInt(30) == 0);

		if (insn.hasSegmentPrefix()) {
			rng.pushInt(0);
			RegisterSet.SEGMENT.reverse(rng, insn.getSegmentPrefix());
		} else {
			rng.pushInt(1);
		}

//		if ( rng.nextBoolean()) {
//			switch (rng.nextInt(3)) {
//				case 0 -> insn.setRepnePrefix(true);
//				case 1 -> insn.setRepePrefix(true);
//				case 2 -> insn.setRepPrefix(true);
//			}
//		}
	}

	public boolean fulfilledBy(boolean evex, ResourcePartition rp) {
		if ((!evex && prefixes.contains(Prefix.EVEX))) return false;
		for (Operand n : operands) {
			if (!n.fulfilledBy(rp)) {
				return false;
			}
		}
		return true;
	}

	public Instruction configureRandomly(RandomGenerator random, ResourcePartition rp) {
		var insn = Instruction.create(icedVariant);
		int explicitOpIdx = 0;

		try {
			for (var operand : operands) {
				switch (operand) {
					case Operand.Counted counted ->
							counted.select(random, insn, explicitOpIdx++, rp);
					case Operand.Uncounted uncounted -> uncounted.select(random, insn, rp);
					case Operand.SuppressedOperand _ -> {
					}
				}
			}
		} catch (NoPossibilitiesException e) {
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

	public enum Prefix {
		D3NOW,
		EVEX,
		VEX,
		XOP
	}
}
