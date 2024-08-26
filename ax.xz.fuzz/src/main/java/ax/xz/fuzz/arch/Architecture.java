package ax.xz.fuzz.arch;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.mutate.Mutator;
import ax.xz.fuzz.runtime.Config;
import ax.xz.fuzz.runtime.ExecutableSequence;
import ax.xz.fuzz.runtime.ExecutionResult;

import java.lang.foreign.MemorySegment;
import java.util.ServiceLoader;
import java.util.random.RandomGenerator;

public interface Architecture {
	RegisterDescriptor registerByIndex(int index);

	RegisterDescriptor registerByName(String name);

	RegisterSet trackedRegisters();

	RegisterSet validRegisters();

	RegisterSet[] subregisterSets();

	Opcode[] allOpcodes();

	RegisterDescriptor defaultCounter();

	RegisterDescriptor stackPointer();

	ExecutionResult runSegment(MemorySegment code, CPUState initialState);

	BranchDescription unconditionalJump();

	BranchDescription randomBranchType(ResourcePartition master, RandomGenerator rng) throws NoPossibilitiesException;

	Mutator[] allMutators();

	int encode(ExecutableSequence sequence, MemorySegment code, Config config);

	String disassemble(byte[] code);

	boolean interestingMismatch(ExecutionResult a, ExecutionResult b);

	int registerIndex(RegisterDescriptor descriptor);

	static Architecture nativeArchitecture() {
		class Holder {
			static Architecture arch;

			static {
				var loader = ServiceLoader.load(ArchitectureProvider.class);

				for (var provider : loader) {
					if (provider.isAvailable())
						Holder.arch = provider.getArchitecture();
				}
			}
		}

		if (Holder.arch != null)
			return Holder.arch;
		else
			throw new UnsupportedOperationException("No available architecture");
	}


	static ScopedValue<Architecture> activeArchitecture = ScopedValue.newInstance();

	static Architecture activeArchitecture() {
		return activeArchitecture.orElseThrow(() -> new UnsupportedOperationException("No active architecture"));
	}

	static void withArchitecture(Architecture arch, Runnable block) {
		ScopedValue.runWhere(activeArchitecture, arch, block);
	}
}
