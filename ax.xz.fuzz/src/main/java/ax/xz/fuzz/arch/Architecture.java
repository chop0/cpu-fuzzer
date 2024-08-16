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
	RegisterDescriptor registerByIndex(int index);

	RegisterDescriptor registerByName(String name);

	RegisterSet trackedRegisters();

	RegisterSet validRegisters();

	RegisterSet[] subregisterSets();

	Opcode[] allOpcodes();

	RegisterDescriptor defaultCounter();

	RegisterDescriptor stackPointer();

	BranchType unconditionalJump();

	BranchType[] allBranchTypes();

	Mutator[] allMutators();

	int encode(ExecutableSequence sequence, long exitAddress, MemorySegment code, Config config);

	String disassemble(byte[] code);

	boolean interestingMismatch(ExecutionResult a, ExecutionResult b);

	static Architecture nativeArch() {
		class Holder {
			static Architecture arch;

			static {
				var loader = ServiceLoader.load(ArchitectureProvider.class);

				for (var provider : loader) {
					if (provider.isAvailable())
						Holder.arch = provider.nativeArchitecture();
				}
			}
		}

		if (Holder.arch != null)
			return Holder.arch;
		else
			throw new UnsupportedOperationException("No available architecture");
	}
}
