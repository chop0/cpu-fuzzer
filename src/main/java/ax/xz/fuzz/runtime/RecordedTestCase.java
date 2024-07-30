package ax.xz.fuzz.runtime;

import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.StatusFlag;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.util.StdConverter;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.github.icedland.iced.x86.dec.Decoder;
import com.github.icedland.iced.x86.fmt.StringOutput;
import com.github.icedland.iced.x86.fmt.gas.GasFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public record RecordedTestCase(
	CPUState initialState,
	@JsonDeserialize(converter = TestCaseDeserializer.class)
	@JsonSerialize(converter = InstructionSerializer.class)
	@JacksonXmlElementWrapper(useWrapping = false)
	byte[][][] code1,
	@JsonDeserialize(converter = TestCaseDeserializer.class)
	@JsonSerialize(converter = InstructionSerializer.class)
	@JacksonXmlElementWrapper(useWrapping = false)
	byte[][][] code2,
	long trampolineLocation,
	Branch[] branches,
	long[] scratchRegions
) {
	public RecordedTestCase {
		if (code1 == null) {
			code1 = new byte[0][][];
		}
		if (code2 == null) {
			code2 = new byte[0][][];
		}
	}

	public String toXML() {
		var mapper = new XmlMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		try {
			return mapper.writeValueAsString(this);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	public static RecordedTestCase fromXML(String xml) {
		try {
			var decoder = new XmlMapper();
			return decoder.readValue(xml, RecordedTestCase.class);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static class TestCaseDeserializer extends StdConverter<JsonNode, byte[][][]> {
		@Override
		public byte[][][] convert(JsonNode value) {
			var bks = value.elements().next().get("block");
			if (bks.isArray()) {
				var iter = bks.elements();
				var blocks = new ArrayList<JsonNode>();
				while (iter.hasNext()) {
					blocks.add(iter.next());
				}
				var result = new byte[blocks.size()][][];

				for (int i = 0; i < blocks.size(); i++) {
					result[i] = deserializeBlock(blocks.get(i));
				}

				return result;
			} else {
				return new byte[][][]{deserializeBlock(bks)};
			}
		}

		private byte[][] deserializeBlock(JsonNode n) {
			var instructions = n.get("instruction");
			if (instructions == null) {
				return new byte[0][];
			}

			if (instructions.isArray()) {
				var result = new byte[instructions.size()][];
				for (int i = 0; i < instructions.size(); i++) {
					result[i] = deserializeInstruction(instructions.get(i));
				}
				return result;
			} else if (!instructions.isEmpty()) {
				return new byte[][]{deserializeInstruction(instructions)};
			} else {
				return new byte[0][];
			}

		}

		private byte[] deserializeInstruction(JsonNode n) {
			var text = n.get("code").textValue();
			var bytes = text.split(" ");
			var result = new byte[bytes.length];
			for (int i = 0; i < bytes.length; i++) {
				result[i] = (byte) Integer.parseInt(bytes[i], 16);
			}

			return result;
		}
	}

	public static class InstructionSerializer extends StdConverter<byte[][][], JsonNode> {
		@Override
		public JsonNode convert(byte[][][] c) {
			var node = JsonNodeFactory.instance.objectNode();
			var blocks = node.putArray("block");

			for (byte[][] value : c) {
				var block = blocks.addObject();
				var instructions = block.putArray("instruction");


				for (int i = 0; i < value.length; i++) {
					var insn = instructions.addObject();
					insn.put("mnemonic", disassemble(value[i]));

					StringBuilder code = new StringBuilder();
					for (var b : value[i]) {
						code.append(String.format("%02x ", b));
					}
					insn.put("code", code.toString());
				}
			}
			return node;
		}
	}

	private static String disassemble(byte[] code) {
		var sb = new StringBuilder();
		new GasFormatter().format(new Decoder(64, code).decode(), new StringOutput(sb));
		return sb.toString();
	}

	public static void main(String[] args) {
		var tester = Tester.create(Config.defaultConfig(), RegisterSet.ALL_EVEX, StatusFlag.all());
		var result = tester.runTest(null, true).getValue().orElseThrow();
		var xml = result.toXML();
		var result2 = RecordedTestCase.fromXML(xml);

		System.out.println(result.equals(result2));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof RecordedTestCase that)) return false;

		return trampolineLocation == that.trampolineLocation && Arrays.deepEquals(code1, that.code1) && Arrays.deepEquals(code2, that.code2) && Arrays.equals(branches, that.branches) && Objects.equals(initialState, that.initialState) && Arrays.equals(scratchRegions, that.scratchRegions);
	}

	@Override
	public int hashCode() {
		int result = Objects.hashCode(initialState);
		result = 31 * result + Arrays.deepHashCode(code1);
		result = 31 * result + Arrays.deepHashCode(code2);
		result = 31 * result + Long.hashCode(trampolineLocation);
		result = 31 * result + Arrays.hashCode(branches);
		result = 31 * result + Arrays.hashCode(scratchRegions);
		return result;
	}
}
