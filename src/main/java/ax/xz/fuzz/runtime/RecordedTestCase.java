package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.BasicBlock;
import ax.xz.fuzz.blocks.Block;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.StatusFlag;
import ax.xz.fuzz.runtime.state.CPUState;
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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static ax.xz.fuzz.runtime.MemoryUtils.Protection.*;
import static ax.xz.fuzz.runtime.MemoryUtils.mmap;

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
	long codeLocation,
	Branch[] branches,
	SerialisedRegion... memory
) {
	public RecordedTestCase {
		if (code1 == null) {
			code1 = new byte[0][][];
		}
		if (code2 == null) {
			code2 = new byte[0][][];
		}
	}

	public Block[] blocksA() {
		return Arrays.stream(code1).map(BasicBlock::ofEncoded).toArray(Block[]::new);
	}

	public Block[] blocksB() {
		return Arrays.stream(code2).map(BasicBlock::ofEncoded).toArray(Block[]::new);
	}

	public long encodedSize() {
		return Arrays.stream(code1).flatMap(Arrays::stream).mapToInt(b -> b.length).sum() +
		       Arrays.stream(code2).flatMap(b -> Arrays.stream(b)).mapToInt(b -> b.length).sum();
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

	public static String disassemble(byte[] code) {
		var sb = new StringBuilder();
		new GasFormatter().format(new Decoder(64, code).decode(), new StringOutput(sb));
		return sb.toString();
	}

	public static void main(String[] args) {
		var tester = Tester.create(Config.defaultConfig());
		var result = tester.record(tester.runTest().tc());
		var xml = result.toXML();
		var result2 = RecordedTestCase.fromXML(xml);

		System.out.println(result.equals(result2));
	}

	public record SerialisedRegion(byte[] compressedData, long start, long size) {
		public static SerialisedRegion ofRegion(MemorySegment region) {
			ByteBuffer output = ByteBuffer.allocate((int) region.byteSize());

			var def = new Deflater();
			def.setLevel(Deflater.BEST_COMPRESSION);

			try {
				def.setInput(region.asByteBuffer());
				def.finish();
				while (def.deflate(output) > 0) {
					if (output.remaining() == 0) {
						var newOutput = ByteBuffer.allocate(output.capacity() * 2);
						output.flip();
						newOutput.put(output);
						output = newOutput;
					}
				}
			} finally {
				def.end();
			}

			byte[] data;
			if (output.remaining() == 0)
				data = output.array();
			else {
				data = new byte[output.position()];
				output.flip();
				output.get(data);
			}

			return new SerialisedRegion(data, region.address(), region.byteSize());
		}

		public MemorySegment allocate(Arena arena) throws DataFormatException {
			var output = mmap(arena, MemorySegment.ofAddress(start), size, READ, WRITE, EXECUTE);

			var inflater = new Inflater();
			try {
				inflater.setInput(compressedData);
				inflater.inflate(output.asByteBuffer());
			} finally {
				inflater.end();
			}

			return output;
		}

		public byte[] decompressed() throws DataFormatException {
			var output = new byte[(int) size];

			var inflater = new Inflater();
			try {
				inflater.setInput(compressedData);
				inflater.inflate(output);
			} finally {
				inflater.end();
			}

			return output;
		}
	}
}
