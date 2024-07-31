package ax.xz.fuzz.blocks;

import ax.xz.fuzz.runtime.Branch;
import ax.xz.fuzz.runtime.CPUState;
import ax.xz.fuzz.runtime.ExecutableSequence;
import ax.xz.fuzz.runtime.RecordedTestCase;

import java.util.Arrays;
import java.util.Objects;

public record InvarianceTestCase(BlockPair[] pairs, Branch[] branches, ExecutableSequence[] sequences, CPUState initialState) {
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof InvarianceTestCase that)) return false;

		return Arrays.equals(pairs, that.pairs) && Arrays.equals(branches, that.branches) && Objects.equals(initialState, that.initialState) && Arrays.equals(sequences, that.sequences);
	}

	@Override
	public int hashCode() {
		int result = Arrays.hashCode(pairs);
		result = 31 * result + Arrays.hashCode(branches);
		result = 31 * result + Arrays.hashCode(sequences);
		result = 31 * result + Objects.hashCode(initialState);
		return result;
	}

	@Override
	public String toString() {
		return "InvarianceTestCase{" +
		       "branches=" + Arrays.toString(branches) +
		       ", sequences=" + Arrays.toString(sequences) +
		       ", initialState=" + initialState +
		       '}';
	}
}
