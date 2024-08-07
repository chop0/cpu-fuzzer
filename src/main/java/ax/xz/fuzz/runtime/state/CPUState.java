package ax.xz.fuzz.runtime.state;

import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.runtime.Architecture;

import java.util.*;

public record CPUState( Map<RegisterDescriptor, byte[]> values) {
	public List<RegisterDifference> diff(CPUState other) {
		var differences = new ArrayList<RegisterDifference>();

		for (var entry : values.entrySet()) {
			var register = entry.getKey();
			var valueA = entry.getValue();
			var valueB = other.values.get(register);

			if (!Arrays.equals(valueA, valueB)) {
				differences.add(new RegisterDifference(register, valueA, valueB));
			}
		}

		return differences;
	}

	public static CPUState zeroed() {
		return new CPUState(Map.of());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}

		CPUState cpuState = (CPUState) obj;
		for (var entry : values.entrySet()) {
			var register = entry.getKey();
			var valueA = entry.getValue();
			var valueB = cpuState.values.get(register);

			if (!Arrays.equals(valueA, valueB)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		var sorted = values.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().index())).toList();
		var sb = new StringBuilder();
		for (var entry : sorted) {
			sb.append(entry.getKey()).append(": ");
			for (var b : entry.getValue()) {
				sb.append(String.format("%02x", b));
			}
			sb.append("\n");
		}

		return sb.toString();
	}
}
