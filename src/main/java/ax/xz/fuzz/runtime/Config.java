package ax.xz.fuzz.runtime;

import ax.xz.fuzz.instruction.RegisterDescriptor;

import java.util.concurrent.ForkJoinPool;

import static ax.xz.fuzz.instruction.x86.x86RegisterDescriptor.R15;

public record Config(int threadCount, int blockCount, int maxInstructionCount, int branchLimit, RegisterDescriptor counterRegister) {
	public static Config defaultConfig() {
		return new Config(ForkJoinPool.getCommonPoolParallelism(), 2, 10, 100, R15);
	}

}
