package ax.xz.fuzz;

import ax.xz.fuzz.mutate.Mutator;
import ax.xz.fuzz.runtime.*;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.zip.DataFormatException;

import static ax.xz.fuzz.arch.Architecture.getArchitecture;
import static ax.xz.fuzz.runtime.Config.defaultConfig;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Help.Visibility.ALWAYS;
import static picocli.CommandLine.Option;

@Command(description = "Attempts to reproduce a test case", name = "reproduce", mixinStandardHelpOptions = true)
public class TriageCommand implements Callable<Void> {
	@CommandLine.Parameters(index = "0", description = "The file to run")
	public Path file;

	@Option(showDefaultValue = ALWAYS, names = {"-a", "--attempts"}, defaultValue = "1", description = "The number of tries before we give up looking for a divergence")
	public int attempts;

	@Option(showDefaultValue = ALWAYS, names = {"-b", "--branch-limit"}, description = "The number of branches a test case can take before it is forcibly terminated")
	public int branchLimit = defaultConfig().branchLimit();


	@Override
	public Void call() throws IOException, DataFormatException {
		var tc = RecordedTestCase.fromXML(Files.readString(file));

		var defaultConfig = defaultConfig();
		var config = new Config(
			defaultConfig.mutators(),
			defaultConfig.threadCount(),
			defaultConfig.blockCount(),
			defaultConfig.maxInstructionCount(),
			branchLimit,
			defaultConfig.counterRegister()
		);

		var tester = SequenceExecutor.forRecordedCase(config, tc);

		var branches = tc.branches();
		var sequenceA = new ExecutableSequence(tc.blocksA(), branches);
		var sequenceB = new ExecutableSequence(tc.blocksB(), branches);

		System.out.println(tester.lookForMismatch(tc, attempts));
		ExecutionResult lastResult = tester.runSequence(tc.initialState(), sequenceA).result();

		for (int i = 0; i < attempts; i++) {
			var result2 = tester.runSequence(tc.initialState(), sequenceB).result();

			System.out.println(lastResult);
			System.out.println(result2);

			if (getArchitecture().interestingMismatch(lastResult, result2)) {
				System.out.println("Interesting mismatch found");

				if (lastResult instanceof ExecutionResult.Success(var A) && result2 instanceof ExecutionResult.Success(var B)) {
					System.out.println(A.diff(B));
				}

				System.exit(0);
			}
		}

		System.out.printf("Failed to find interesting mismatch after %d attempts%n", attempts);
		System.exit(1);
		return null;
	}
}
