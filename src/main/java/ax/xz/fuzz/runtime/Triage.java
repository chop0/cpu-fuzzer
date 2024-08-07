package ax.xz.fuzz.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DataFormatException;

import static ax.xz.fuzz.runtime.Architecture.getArchitecture;

public class Triage {
	public static void runFile(Path file, int attempts) throws IOException, DataFormatException {
		var tc = RecordedTestCase.fromXML(Files.readString(file));

		var tester = SequenceExecutor.forRecordedCase(Config.defaultConfig(), tc);

		var branches = tc.branches();
		var sequenceA = new ExecutableSequence(tc.blocksA(), branches);
		var sequenceB = new ExecutableSequence(tc.blocksB(), branches);

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
	}
}
