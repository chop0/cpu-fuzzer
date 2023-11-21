package ax.xz.fuzz.encoding;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record EvexConstraint(String functionality, MaskMode maskMode, String tupleType, int elementSize, int vectorLength, boolean isControlMask, boolean acceptsZeroMask, boolean forceZeroMask, String staticBroadcast) {

	enum MaskMode {
		ALLOWED, FORBIDDEN, REQUIRED
	}

	@JsonCreator
	public static EvexConstraint of(
			@JsonProperty("functionality") String functionality,
			@JsonProperty("mask_mode") MaskMode maskMode,
			@JsonProperty("tuple_type") String tupleType,
			@JsonProperty("element_size") int elementSize,
			@JsonProperty("vector_length") int vectorLength,
			@JsonProperty(value = "mask_flags", defaultValue = "[]") String[] maskFlags,
			@JsonProperty("static_broadcast") String staticBroadcast
	) {
		if (maskFlags == null) maskFlags = new String[0];

		boolean isControlMask = false;
		boolean forceZeroMask = false;
		boolean acceptsZeroMask = false;

		for (String flag : maskFlags) {
			switch (flag) {
				case "is_control_mask" -> isControlMask = true;
				case "accepts_zero_mask" -> acceptsZeroMask = true;
				case "force_zero_mask" -> forceZeroMask = true;
				default -> throw new IllegalArgumentException("Unknown mask flag: " + flag);
			}
		}

		return new EvexConstraint(functionality, maskMode, tupleType, elementSize, vectorLength, isControlMask, acceptsZeroMask, forceZeroMask, staticBroadcast);
	}

}
