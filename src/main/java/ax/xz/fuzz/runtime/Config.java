package ax.xz.fuzz.runtime;

import java.util.concurrent.ForkJoinPool;

import static com.github.icedland.iced.x86.Register.R15;

public record Config(int threadCount, int blockCount, int maxInstructionCount, int branchLimit, int counterRegister) {
	public static Config defaultConfig() {
		return new Config(ForkJoinPool.getCommonPoolParallelism(), 2, 10, 100, R15);
	}

}
