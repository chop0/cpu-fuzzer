package ax.xz.fuzz.encoding;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonKey;
import com.fasterxml.jackson.annotation.JsonProperty;

public sealed interface Filter {
	record Mode(int mode, boolean inverted) implements Filter {}

	record ModrmMod(int mod, boolean inverted) implements Filter {}
	record ModrmReg(int reg, boolean inverted) implements Filter {}
	record ModrmRm(int modrm, boolean inverted) implements Filter {}

	record MandatoryPrefix(byte prefix) implements Filter {}
	record RexMask(byte mask, byte value) implements Filter {}
	record EvexMask(int mask, int value) implements Filter {}
	record MvexMask(int mask, int value) implements Filter {}
	record VectorLength(int value) implements Filter {}

	record AddressSize(int value, boolean inverted) implements Filter {}
	record OperandSize(int value, boolean inverted) implements Filter {}

	record Feature(String name, boolean enabled) implements Filter {}

	record LegacyPrefix(int group, boolean enabled) implements Filter {}
	record ForceModrmReg() implements Filter {}
	record ForceModrmRm() implements Filter {}

	@JsonCreator
	static Filter of(String key, String value) {
		return switch (key) {
			case "mode" -> {
				boolean inverted = value.startsWith("!");
				int mode = Integer.parseInt(inverted ? value.substring(1) : value);
				yield new Mode(mode, inverted);
			}
			case "modrm_mod" -> {
				boolean inverted = value.startsWith("!");
				int mod = Integer.parseInt(inverted ? value.substring(1) : value);
				yield new ModrmMod(mod, inverted);
			}
			case "modrm_reg" -> {
				boolean inverted = value.startsWith("!");
				int reg = Integer.parseInt(inverted ? value.substring(1) : value);
				yield new ModrmReg(reg, inverted);
			}
			case "modrm_rm" -> {
				boolean inverted = value.startsWith("!");
				int rm = Integer.parseInt(inverted ? value.substring(1) : value);
				yield new ModrmRm(rm, inverted);
			}
			case "force_modrm_reg" -> new ForceModrmReg();
			case "force_modrm_rm" -> new ForceModrmRm();
			case "mandatory_prefix" -> {
				if (value.equals("none") || value.equals("ignore")) // TODO: revisit
					yield null;
				else yield new MandatoryPrefix((byte) Integer.parseInt(value, 16));
			}
			case String s when s.startsWith("feature") -> new Feature(s.replace("feature_", ""), value.equals("1"));
			case "vector_length" -> new VectorLength(Integer.parseInt(value));

			case "rex_w" -> new RexMask((byte) 0b1000, (byte) (value.equals("1") ? 0b1000 : 0));
			case "rex_b" -> new RexMask((byte) 0b0100, (byte) (value.equals("1") ? 0b0100 : 0));
			case "evex_b" -> new EvexMask(0b0010_0000_0000_0000, value.equals("1") ? 0b0010_0000_0000_0000 : 0);
			case "mvex_e" -> new MvexMask(0, 0); // todo: revisit

			case "address_size" -> {
				boolean inverted = value.startsWith("!");
				int size = Integer.parseInt(inverted ? value.substring(1) : value);
				yield new AddressSize(size, inverted);
			}

			case "operand_size" -> {
				boolean inverted = value.startsWith("!");
				int size = Integer.parseInt(inverted ? value.substring(1) : value);
				yield new OperandSize(size, inverted);
			}
			case String s when s.startsWith("prefix_group") -> new LegacyPrefix(Integer.parseInt(s.replace("prefix_group", "")), value.equals("1"));
			default -> throw new IllegalArgumentException("Unknown filter: " + key);
		};
	}
}
