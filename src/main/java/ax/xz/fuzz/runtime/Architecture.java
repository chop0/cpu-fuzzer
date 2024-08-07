package ax.xz.fuzz.runtime;

import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.x86.X86Architecture;
import ax.xz.fuzz.runtime.state.CPUState;

import java.lang.foreign.MemorySegment;

public interface Architecture {
	static Architecture getArchitecture() {
		return X86Architecture.ofNative();
	}

	RegisterDescriptor registerByIndex(int index);
	RegisterSet trackedRegisters();
	RegisterSet validRegisters();

	RegisterDescriptor defaultCounter();

	RegisterDescriptor stackPointer();
	ExecutionResult runSegment(MemorySegment code, CPUState initialState);

	boolean interestingMismatch(ExecutionResult a, ExecutionResult b);
}
