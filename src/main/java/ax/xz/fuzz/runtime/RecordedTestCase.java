package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.*;
import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.instruction.x86.x86RegisterDescriptor;
import ax.xz.fuzz.runtime.state.CPUState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.StdSubtypeResolver;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.util.StdConverter;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.github.icedland.iced.x86.dec.Decoder;
import com.github.icedland.iced.x86.fmt.StringOutput;
import com.github.icedland.iced.x86.fmt.gas.GasFormatter;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static ax.xz.fuzz.runtime.Architecture.getArchitecture;
import static ax.xz.fuzz.runtime.MemoryUtils.Protection.*;
import static ax.xz.fuzz.runtime.MemoryUtils.mmap;

public record RecordedTestCase(
	CPUState initialState,
	@JsonDeserialize(converter = TestCaseDeserializer.class)
	@JsonSerialize(converter = InstructionSerializer.class)
	@JacksonXmlElementWrapper(useWrapping = false)
	Block[] code1,
	@JsonDeserialize(converter = TestCaseDeserializer.class)
	@JsonSerialize(converter = InstructionSerializer.class)
	@JacksonXmlElementWrapper(useWrapping = false)
	Block[] code2,
	long codeLocation,
	Branch[] branches,
	SerialisedRegion... memory
) implements TestCase {
	public RecordedTestCase {
		if (code1 == null) {
			code1 = new Block[0];
		}
		if (code2 == null) {
			code2 = new Block[0];
		}
	}

	public Block[] blocksA() {
		return code1;
	}

	public Block[] blocksB() {
		return code2;
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
			var simpleModule = new SimpleModule();
			simpleModule.addKeyDeserializer(RegisterDescriptor.class, new RegisterDescriptorDeserializer());
			decoder.registerModule(simpleModule);

			return decoder.readValue(xml, RecordedTestCase.class);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static class RegisterDescriptorDeserializer extends KeyDeserializer {

		@Override
		public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
			return x86RegisterDescriptor.fromString(key);
		}
	}

	public static class TestCaseDeserializer extends StdConverter<JsonNode, Block[]> {
		@Override
		public Block[] convert(JsonNode value) {
			var bks = value.elements().next().get("block");
			if (bks.isArray()) {
				var iter = bks.elements();
				var blocks = new ArrayList<JsonNode>();
				while (iter.hasNext()) {
					blocks.add(iter.next());
				}
				var result = new Block[blocks.size()];

				for (int i = 0; i < blocks.size(); i++) {
					result[i] = deserializeBlock(blocks.get(i));
				}

				return result;
			} else {
				return new Block[]{deserializeBlock(bks)};
			}
		}

		private Block deserializeBlock(JsonNode n) {
			var instructions = n.get("instruction");
			if (instructions == null) {
				return new BasicBlock(List.of());
			}

			if (instructions.isArray()) {
				var result = new ArrayList<BlockEntry>(instructions.size());

				for (int i = 0; i < instructions.size(); i++) {
					result.add(new BlockEntry.ConcreteEntry(deserializeInstruction(instructions.get(i))));
				}

				return new BasicBlock(result);
			} else if (!instructions.isEmpty()) {
				return new BasicBlock(List.of(new BlockEntry.ConcreteEntry(deserializeInstruction(instructions))));
			} else {
				return new BasicBlock(List.of());
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

	public static class InstructionSerializer extends StdConverter<Block[], JsonNode> {
		@Override
		public JsonNode convert(Block[] c) {
			var node = JsonNodeFactory.instance.objectNode();
			var blocks = node.putArray("block");

			try {
				for (var value : c) {
					var block = blocks.addObject();
					var instructions = block.putArray("instruction");


					for (var item : value.items()) {
						var bytes = item.encode(0);
						var insn = instructions.addObject();
						insn.put("mnemonic", disassemble(bytes));

						StringBuilder code = new StringBuilder();
						for (var b : bytes) {
							code.append(String.format("%02x ", b));
						}
						insn.put("code", code.toString());
					}
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
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
