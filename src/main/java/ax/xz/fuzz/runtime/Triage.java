package ax.xz.fuzz.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DataFormatException;

import static ax.xz.fuzz.runtime.ExecutionResult.interestingMismatch;

public class Triage {
	public static void runFile(Path file) throws IOException, DataFormatException {
		var tc = RecordedTestCase.fromXML(Files.readString(file));

		var tester = SequenceExecutor.forRecordedCase(Config.defaultConfig(), tc);

		var branches = tc.branches();
		var sequenceA = new ExecutableSequence(tc.blocksA(), branches);
		var sequenceB = new ExecutableSequence(tc.blocksB(), branches);

		var result1 = tester.runSequence(tc.initialState(), sequenceA).result();
		var result2 = tester.runSequence(tc.initialState(), sequenceB).result();

		System.out.println(result1);
		System.out.println(result2);
		System.out.println(interestingMismatch(result1, result2));

		if (result1 instanceof ExecutionResult.Success(var A) && result2 instanceof ExecutionResult.Success(var B)) {
			System.out.println(A.diff(B));
		}

		if (!interestingMismatch(result1, result2))
			System.exit(1);
	}
}
