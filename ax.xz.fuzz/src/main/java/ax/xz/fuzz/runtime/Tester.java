package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.InvarianceTestCase;
import ax.xz.fuzz.blocks.ProgramRandomiser;
import ax.xz.fuzz.instruction.MemoryPartition;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.instruction.StatusFlag;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static ax.xz.fuzz.arch.Architecture.getArchitecture;

public class Tester {
	private final SequenceExecutor executor;
	private final ProgramRandomiser randomiser;
	private final RandomGenerator rng;

	private final ResourcePartition partition;

	public Tester(SequenceExecutor executor, ProgramRandomiser randomiser, RandomGenerator rng, ResourcePartition partition) {
		this.executor = executor;
		this.randomiser = randomiser;
		this.rng = rng;
		this.partition = partition;
	}

	public TestResult runTest() {
		var tc = randomiser.selectTestCase(rng, partition);

		var a = executor.runSequence(tc.initialState(), tc.a());
		var b = executor.runSequence(tc.initialState(), tc.b());

		return new TestResult(a.result(), b.result(), tc);
	}

	public TestCase minimise(InvarianceTestCase tc) {
		var minimiser = new Minimiser(executor, 20);
		return minimiser.minimise(tc);
	}

	public RecordedTestCase record(InvarianceTestCase tc) {
		return executor.record(tc);
	}

	public record TestResult(ExecutionResult a, ExecutionResult b, InvarianceTestCase tc) {
		public boolean hasInterestingMismatch() {
			return getArchitecture().interestingMismatch(a, b);
		}
	}

	public static Tester create(Config config) {
		var executor = SequenceExecutor.create(config);

		var registers = executor.legallyModifiableRegisters();
		var flags = StatusFlag.all();

		var partition = new ResourcePartition(flags, registers.subtract(config.counterRegister().related()), MemoryPartition.of(executor.primaryScratch()), executor.stack());
		return new Tester(
			executor,
			new ProgramRandomiser(config),
			new SplittableRandom(),
			partition
		);
	}
}
