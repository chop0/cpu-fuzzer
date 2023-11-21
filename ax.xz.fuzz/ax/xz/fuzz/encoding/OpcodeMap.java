package ax.xz.fuzz.encoding;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum OpcodeMap {
	ONE_BYTE,
	_0F0F,
	_0F38,
	_OF3A,
	_0F,
	XOPA,
	XOP8,
	XOP9,
	MAP5,
	MAP6;

	@JsonCreator
	public static OpcodeMap fromString(String s) {
		return switch (s.toLowerCase()) {
			case "0f0f" -> _0F0F;
			case "0f38" -> _0F38;
			case "0f3a" -> _OF3A;
			case "0f" -> _0F;
			case "xopa" -> XOPA;
			case "xop8" -> XOP8;
			case "xop9" -> XOP9;
			case "map5" -> MAP5;
			case "map6" -> MAP6;
			default -> throw new IllegalArgumentException("Unknown opcode map: " + s);
		};
	}
}
