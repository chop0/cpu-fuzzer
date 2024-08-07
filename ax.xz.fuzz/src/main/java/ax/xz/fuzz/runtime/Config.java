package ax.xz.fuzz.runtime;

import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.mutate.Mutator;

import java.util.concurrent.ForkJoinPool;

import static ax.xz.fuzz.arch.Architecture.getArchitecture;

public record Config(Mutator[] mutators, int threadCount, int blockCount, int maxInstructionCount, int branchLimit, RegisterDescriptor counterRegister) {
	public static Config defaultConfig() {
		return new Config(getArchitecture().allMutators(), ForkJoinPool.getCommonPoolParallelism(), 2, 10, 100, getArchitecture().defaultCounter());
	}

}
