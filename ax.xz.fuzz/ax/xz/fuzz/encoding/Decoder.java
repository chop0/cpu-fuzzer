package ax.xz.fuzz.encoding;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Decoder {
	private static final Instruction[] instructions;
	private static final Map<OpcodeMap, Instruction[]> opcodes = new HashMap<>();
	static {
		var json = new ObjectMapper()
				.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
				.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);

		try {
			instructions = json.readValue(Path.of("instructions.json").toFile(), Instruction[].class);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		for (Instruction instruction : instructions) {
			var map = opcodes.computeIfAbsent(instruction.opcodeMap(), _ -> new Instruction[256]);
			map[instruction.opcode()] = instruction;
		}

	}

	private byte[] prefixes;

	public void decode(byte[] instruction) {
		int i;

		for (i = 0; isPrefix(instruction[i]); i++);
		prefixes = new byte[i];
		System.arraycopy(instruction, 0, prefixes, 0, i);

		OpcodeMap opcodeMap = OpcodeMap.ONE_BYTE;
		int opcode;

		if (instruction[i] == 0x0F) {
			opcodeMap = switch (instruction[i + 1]) {
				case 0x38 -> OpcodeMap._0F38;
				case 0x3A -> OpcodeMap._OF3A;
				case 0x0F -> OpcodeMap._0F0F;
				default -> OpcodeMap._0F;
			};
		}

		opcode = switch (opcodeMap) {
			case _0F0F, _0F38, _OF3A -> instruction[i + 2];
			case _0F -> instruction[i + 1];
			case ONE_BYTE -> instruction[i];
			default -> throw new IllegalStateException("unsupported opcode bank: " + opcodeMap);
		};

		var instructionObject = opcodes.get(opcodeMap)[opcode];
		if (instructionObject == null)
			throw new IllegalStateException("unknown opcode: " + opcodeMap + " " + opcode);
	}

	private boolean isPrefix(byte b) {
		// rex
		if ((b & 0b11110000) == 0b01000000)
			return true;

		if (b == 0x66 || b == 0x67 || b == (byte)0xF0 || b == (byte)0xF2 || b == (byte)0xF3
			|| b == (byte) 0x2e || b == (byte) 0x36 || b == (byte) 0x3e || b == (byte) 0x26
|| b == (byte) 0x64 || b == (byte) 0x65 || b == (byte) 0x2e || b == (byte) 0x3e
		)
			return true;

		return false;
	}
}
