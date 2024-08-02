package ax.xz.fuzz.runtime.state;

import ax.xz.fuzz.tester.saved_state;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

// TODO: include segment registers
public record CPUState(GeneralPurposeRegisters gprs, VectorRegisters zmm, MMXRegisters mmx, long rflags) {
	public List<RegisterDifference> diff(CPUState other) {
		var differences = new ArrayList<RegisterDifference>();
		differences.addAll(gprs.diff(other.gprs));
		differences.addAll(zmm.diff(other.zmm));
		differences.addAll(mmx.diff(other.mmx));
		return differences;
	}

	public static CPUState ofSavedState(MemorySegment savedState) {
		return new CPUState(
				GeneralPurposeRegisters.ofSavedState(savedState),
				VectorRegisters.ofArray(saved_state.zmm(savedState)),
				MMXRegisters.ofArray(saved_state.mm(savedState)),
				saved_state.rflags(savedState)
		);
	}

	public void toSavedState(MemorySegment savedState) {
		gprs.toSavedState(savedState);
		zmm.toArray(saved_state.zmm(savedState));
		mmx.toArray(saved_state.mm(savedState));
		saved_state.rflags(savedState, rflags);
	}

	public static CPUState filledWith(long thing) {
		return new CPUState(
				GeneralPurposeRegisters.filledWith(thing),
				VectorRegisters.filledWith(thing),
				MMXRegisters.filledWith(thing),
				0
		);
	}

	public static CPUState random(RandomGenerator rng) {
		return new CPUState(
				GeneralPurposeRegisters.random(rng),
				VectorRegisters.random(rng),
				MMXRegisters.random(rng),
				0
		);
	}

}
