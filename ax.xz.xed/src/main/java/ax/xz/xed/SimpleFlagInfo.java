package ax.xz.xed;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.EnumSet;

import static ax.xz.xed.xed.xed_simple_flag_get_read_flag_set;
import static ax.xz.xed.xed.xed_simple_flag_get_written_flag_set;
import static ax.xz.xed.xed.xed_simple_flag_get_undefined_flag_set;
import static ax.xz.xed.xed.xed_flag_set_print;

public record SimpleFlagInfo(EnumSet<InstructionStatusFlag> read, EnumSet<InstructionStatusFlag> written, EnumSet<InstructionStatusFlag> undefined) implements FlagInfo {
	public static SimpleFlagInfo empty() {
		return new SimpleFlagInfo(EnumSet.noneOf(InstructionStatusFlag.class), EnumSet.noneOf(InstructionStatusFlag.class), EnumSet.noneOf(InstructionStatusFlag.class));
	}

	public static SimpleFlagInfo fromSimpleFlag(MemorySegment m) {
		var read = getFlagSet(xed_simple_flag_get_read_flag_set(m));
		var written = getFlagSet(xed_simple_flag_get_written_flag_set(m));
		var undefined = getFlagSet(xed_simple_flag_get_undefined_flag_set(m));

		return new SimpleFlagInfo(read, written, undefined);
	}

	private static EnumSet<InstructionStatusFlag> getFlagSet(MemorySegment m) {
		try (var arena = Arena.ofConfined()) {
			var buf = arena.allocate(512);
			int written = 512 - xed_flag_set_print(m, buf, 512);

			if (written == 512) {
				throw new AssertionError("Flag set string too long");
			}

			return parseFlagList(buf);

		}
	}

	private static EnumSet<InstructionStatusFlag> parseFlagList(MemorySegment buf) {
		EnumSet<InstructionStatusFlag> flags = EnumSet.noneOf(InstructionStatusFlag.class);

		var str = buf.getString(0);
		if (str.isEmpty())
			return flags;

		var parts = str.split(" ");
		for (String part : parts) {
			flags.add(InstructionStatusFlag.fromString(part.toLowerCase()));
		}

		return flags;
	}
}
