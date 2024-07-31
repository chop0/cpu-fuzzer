package ax.xz.fuzz.runtime;

import picocli.CommandLine;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

import static picocli.CommandLine.Help.Visibility.ALWAYS;
import static picocli.CommandLine.Model.*;
import static picocli.CommandLine.*;

public final class Config {
	@Option(showDefaultValue = ALWAYS, names = {"-j", "--threads"}, defaultValue = "${ForkJoinPool.getCommonPoolParallelism()}", description = "Number of threads to use")
	public   int threadCount;
	@Option(showDefaultValue = ALWAYS, names = {"-c", "--block-count"}, defaultValue = "${defaultConfig().blockCount()}", description = "Number of interleaved blocks each test case should contain.  Each interleaved block is a combination of two basic blocks.")
	public   int blockCount;
	@Option(showDefaultValue = ALWAYS, names = {"-i", "--block-size"}, defaultValue = "${defaultConfig().maxInstructionCount()}", description = "Maximum number of instructions in each basic block")
	public   int maxInstructionCount;
	@Option(showDefaultValue = ALWAYS, names = {"-b", "--branch-limit"}, defaultValue = "${defaultConfig().branchLimit()}", description = "The number of branches a test case can take before it is forcibly terminated")
	public   int branchLimit;
	@Parameters(arity = "0..*", description = "Path to the file to replay", mapFallbackValue = Option.NULL_VALUE)
	public   Optional<Path> file;

	public Config(int threadCount, int blockCount, int maxInstructionCount, int branchLimit, Optional<Path> file) {
		this.threadCount = threadCount;
		this.blockCount = blockCount;
		this.maxInstructionCount = maxInstructionCount;
		this.branchLimit = branchLimit;
		this.file = file;
	}

	public Config() {
		this.threadCount = ForkJoinPool.getCommonPoolParallelism();
		this.blockCount = 2;
		this.maxInstructionCount = 10;
		this.branchLimit = 100;
		this.file = Optional.empty();
	}

	public static Config defaultConfig() {
		return new Config(ForkJoinPool.getCommonPoolParallelism(), 2, 10, 100, Optional.empty());
	}

	public int threadCount() {
		return threadCount;
	}

	public int blockCount() {
		return blockCount;
	}

	public int maxInstructionCount() {
		return maxInstructionCount;
	}

	public int branchLimit() {
		return branchLimit;
	}

	public Optional<Path> file() {
		return file;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (Config) obj;
		return this.threadCount == that.threadCount &&
		       this.blockCount == that.blockCount &&
		       this.maxInstructionCount == that.maxInstructionCount &&
		       this.branchLimit == that.branchLimit &&
		       Objects.equals(this.file, that.file);
	}

	@Override
	public int hashCode() {
		return Objects.hash(threadCount, blockCount, maxInstructionCount, branchLimit, file);
	}

	@Override
	public String toString() {
		return "Config[" +
		       "threadCount=" + threadCount + ", " +
		       "blockCount=" + blockCount + ", " +
		       "maxInstructionCount=" + maxInstructionCount + ", " +
		       "branchLimit=" + branchLimit + ", " +
		       "file=" + file + ']';
	}

}
