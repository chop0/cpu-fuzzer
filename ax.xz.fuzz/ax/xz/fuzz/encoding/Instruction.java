package ax.xz.fuzz.encoding;

import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.Registers;
import ax.xz.fuzz.instruction.StatusFlag;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.util.StdConverter;
import com.github.icedland.iced.x86.OpKind;

import java.util.*;

import static ax.xz.fuzz.instruction.RegisterSet.*;
import static com.github.icedland.iced.x86.Register.*;

public record Instruction(
		String mnemonic,
		ExceptionClass exceptionClass,
		PrefixFlag[] prefixFlags,

		OpsizeMap opsizeMap,
		OpcodeMap opcodeMap,
		AdsizeMap adsizeMap,

		@JsonDeserialize(converter = ByteConverter.class) byte opcode,
		@JsonDeserialize(converter = FilterDeserialiser.class) Filter[] filters,
		@JsonDeserialize(contentConverter = OperandConverter.class) Operand[] operands,
		MetaInfo metaInfo,
	    @JsonDeserialize(converter = FlagConverter.class) AffectedFlag[] affectedFlags,
		@JsonDeserialize(converter = Base64Converter.class) String comment,
		Encoding encoding,
		Set<InstructionFlag> flags,
		@JsonProperty(defaultValue = "3") int cpl,
		@JsonProperty("mvex") MvexConstraint mvexConstraint,
		@JsonProperty("evex") EvexConstraint evexConstraint,
		@JsonProperty("vex") VexConstraint vexConstraint) {

	enum OpsizeMap {
		BYTEOP, FORCE32_OR_REXW, IGNORE66, FORCE64, DEFAULT64, FORCE32_OR_64, REXW32
	}

	private static class ByteConverter extends StdConverter<String, Byte> {

		@Override
		public Byte convert(String bytes) {
			return (byte) Integer.parseInt(bytes, 16);
		}
	}

	private static class FilterDeserialiser extends StdConverter<Map<String, String>, Filter[]> {
		@Override
		public Filter[] convert(Map<String, String> strings) {
			return strings.entrySet().stream()
					.map(e -> Filter.of(e.getKey(), e.getValue()))
					.filter(Objects::nonNull)
					.toArray(Filter[]::new);
		}
	}

	private static class OperandConverter extends StdConverter<JsonNode, Operand> {

		@Override
		public Operand convert(JsonNode map) {
			return switch (map.get("operand_type").asText()) {
				case "implicit_reg" -> {
					var registerName = map.get("register").asText();
					var reg = Registers.byName(registerName.toUpperCase()
							.replace("OSZ_", "R") // todo: revisit
							.replace("SSZ_", "R") // todo: revisit
							.replace("ASZ_", "R") // todo: revisit
					);

					if (registerName.equals("uif"))
						yield new Operand.SuppressedOperand.StatusFlags(StatusFlag.UIF);

					if (registerName.equals("pkru") || registerName.equals("xcr0"))
						yield null; // TODO: revisit

					if (reg == null) {
						throw new IllegalArgumentException("Unknown register: " + registerName);
					}

					if (!map.has("visible") || map.get("visible").asBoolean())
						yield new Operand.Counted.FixedReg(reg);
					else
						yield new Operand.SuppressedOperand.Reg(reg);
				}
				case "imm" -> new Operand.Counted.Imm(map.get("width64").asInt() * 8, switch (map.get("encoding").asText()) {
					case "simm8", "uimm8" -> OpKind.IMMEDIATE8;
					case "simm16", "uimm16" -> OpKind.IMMEDIATE16;
					case "simm32", "uimm32" -> OpKind.IMMEDIATE32;
					case "simm64", "uimm64" -> OpKind.IMMEDIATE64;
					case "simm16_32_32" -> OpKind.IMMEDIATE32;
					case "simm16_32_64" -> OpKind.IMMEDIATE64;
					case "is4" -> OpKind.IMMEDIATE8;
					default -> throw new IllegalArgumentException("Unknown encoding: " + map.get("encoding").asText());
				});
				case "mem" -> {
					if (map.has("width64"))
						yield new Operand.Counted.Mem(map.get("width64").asInt() * 8);
					else if (map.get("element_type").asText().equals("uint32"))
						yield new Operand.Counted.Mem(32);
					else
						yield new Operand.Counted.Mem(512);
				}

				case "gpr8" -> new Operand.Counted.Reg(RegisterSet.GPB);
				case "gpr16" -> new Operand.Counted.Reg(GPW);
				case "gpr32" -> new Operand.Counted.Reg(GPD);
				case "gpr64" -> new Operand.Counted.Reg(GPQ);
				case "gpr16_32_64" -> new Operand.Counted.Reg(GPQ.union(GPD).union(GPW));
				case "gpr32_32_64" -> new Operand.Counted.Reg(GPQ.union(GPD));
				case "gpr16_32_32" -> new Operand.Counted.Reg(GPD.union(GPW));

				case "bnd" -> new Operand.Counted.Reg(RegisterSet.of(BND0, BND1, BND2, BND3));

				case "agen", "agen_norel" -> new Operand.Counted.Mem(0);

				case "mib" -> new Operand.Counted.Mem(0); // TODO: revisit
				case "ptr" -> new Operand.Counted.Mem(0); // TODO: revisit
				case "rel" -> new Operand.Counted.Mem(0); // TODO: revisit

				case "implicit_mem" -> new Operand.SuppressedOperand.Mem();

				case "gpr_asz" -> new Operand.Counted.Reg(RegisterSet.GPQ.union(GPD).union(GPW)); // TODO: revisit
				case "fpr" -> new Operand.Counted.Reg(ST); // TODO: revisit

				case "mask" -> new Operand.Uncounted.Mask(false); // TODO: zeroing wrong?
				case "sreg" -> new Operand.Counted.Reg(RegisterSet.SEGMENT);
				case "moffs" -> new Operand.Counted.Moffs(map.get("width64").asInt() * 8);

				case "cr" -> new Operand.Counted.Reg(RegisterSet.ofRange(CR0, CR15));
				case "dr" -> new Operand.Counted.Reg(RegisterSet.ofRange(DR0, DR15));

				case "mmx" -> new Operand.Counted.Reg(MM);
				case "xmm" -> new Operand.Counted.Reg(XMM_EVEX);
				case "ymm" -> new Operand.Counted.Reg(YMM_EVEX);
				case "zmm" -> new Operand.Counted.Reg(ZMM_EVEX);
				case "tmm" -> new Operand.Counted.Reg(TMM);

				case String s when s.startsWith("mem_vsib") -> null; // TODO: revisit

				case "implicit_imm1" -> new Operand.Counted.FixedNumber((byte) 1);

				default ->
						throw new IllegalArgumentException("Unknown operand type: " + map.get("operand_type").asText());
			};
		}
	}

	private static class FlagConverter extends StdConverter<JsonNode, AffectedFlag[]> {
		@Override
		public AffectedFlag[] convert(JsonNode j) {
			return j.properties().stream()
					.map(p -> new AffectedFlag(p.getKey(), parse(p.getValue().asText())))
					.toArray(AffectedFlag[]::new);
		}

		private static AffectedFlag.FlagAccess parse(String fa) {
			return switch (fa) {
				case "u" -> new AffectedFlag.FlagAccess.Unknown();
				default -> new AffectedFlag.FlagAccess.Known(fa.contains("t"), fa.contains("m"));
			};

		}
	}

	private static class Base64Converter extends StdConverter<String, String> {
		@Override
		public String convert(String s) {
			return new String(Base64.getDecoder().decode(s));
		}
	}
}
