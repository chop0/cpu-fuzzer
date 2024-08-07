package ax.xz.fuzz.arch;

import ax.xz.fuzz.instruction.RegisterDescriptor;

import java.util.Arrays;

public record RegisterDifference(RegisterDescriptor register, byte[] valueA, byte[] valueB) {
	public RegisterDifference {
		if (valueA.length != valueB.length)
			throw new IllegalArgumentException("Values must have the same length");

		if (Arrays.equals(valueA, valueB))
			throw new IllegalArgumentException("Values must be different");
	}

	public String toString() {
		var builder = new StringBuilder();
		builder.append("Register ").append(register).append(" differs\n");
		builder.append("Value A: ");
		for (byte b : valueA) {
			builder.append(String.format("%02X", b));
		}
		builder.append("\nValue B: ");
		for (byte b : valueB) {
			builder.append(String.format("%02X", b));
		}
		builder.append("\n");

		builder.append(" ".repeat("Value B: ".length()));

		for (int i = 0; i < valueA.length; i++) {
			if (valueA[i] == valueB[i]) {
				builder.append("  ");
			} else {
				builder.append("^^");
			}
		}

		return builder.toString();
	}
}
