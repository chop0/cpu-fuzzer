package ax.xz.fuzz.arch;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.mutate.Mutator;
import ax.xz.fuzz.runtime.Config;
import ax.xz.fuzz.runtime.ExecutableSequence;
import ax.xz.fuzz.runtime.ExecutionResult;

import java.lang.foreign.MemorySegment;
import java.util.ServiceLoader;

public interface Architecture {
	static Architecture getArchitecture() {
		var loader = ServiceLoader.load(ArchitectureProvider.class);

		for (var provider : loader) {
			if (provider.isAvailable())
				return provider.getArchitecture();
		}

		throw new UnsupportedOperationException("No available architecture");
	}

	RegisterDescriptor registerByIndex(int index);
	RegisterDescriptor registerByName(String name);
	RegisterSet trackedRegisters();
	RegisterSet validRegisters();

	RegisterSet[] subregisterSets();
	Opcode[] allOpcodes();

	RegisterDescriptor defaultCounter();

	RegisterDescriptor stackPointer();
	ExecutionResult runSegment(MemorySegment code, CPUState initialState);

	BranchType unconditionalJump();
	BranchType[] allBranchTypes();

	Mutator[] allMutators();

	int encode(ExecutableSequence sequence, MemorySegment code, Config config);
	String disassemble(byte[] code);

	boolean interestingMismatch(ExecutionResult a, ExecutionResult b);
}
