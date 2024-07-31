package ax.xz.fuzz.metrics;

import ax.xz.fuzz.runtime.Tester;
import jdk.jfr.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Label("Fuzz Thread")
@Description("Long-running fuzz thread")
public class FuzzThreadEvent extends Event {
	@FuzzThreadId
	@Label("Thread ID")
	public Thread thread;

	@MemoryAddress
	@Label("Trampoline location")
	public long trampolineLocation;

	@Label("Blocks count")
	@Description("Number of interleaved blocks in each test case.  Each interleaved block comprises two basic blocks.")
	public int blocksPerTestCase;

	@Label("Basic block size")
	@Description("Maximum number of instructions in each basic block")
	public int maxInstructionCount;

	@Label("Test case branch limit")
	@Description("The number of branches a test case can take before it is forcibly terminated")
	public int branchLimit;

	@MetadataDefinition
	@Relational
	@Name("ax.xz.fuzz.FuzzThreadId")
	@Label("Test Sequence ID")
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface FuzzThreadId {
	}
}
