package ax.xz.fuzz.instruction;

import ax.xz.fuzz.runtime.ExecutionResult;
import ax.xz.fuzz.runtime.state.CPUState;

import java.lang.foreign.MemorySegment;

public interface Trampoline {
	ExecutionResult run(CPUState initalState, MemorySegment code);
}
