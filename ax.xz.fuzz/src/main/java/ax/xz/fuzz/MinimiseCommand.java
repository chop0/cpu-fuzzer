package ax.xz.fuzz;

import ax.xz.fuzz.runtime.Minimiser;
import ax.xz.fuzz.runtime.*;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.zip.DataFormatException;

import static ax.xz.fuzz.runtime.Config.defaultConfig;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Help.Visibility.ALWAYS;
import static picocli.CommandLine.Option;

@Command(description = "Attempts to minimise a test case", name = "minimise")
public class MinimiseCommand implements Callable<Void> {
	@CommandLine.Parameters(index = "0", paramLabel = "<input>", description = "The file to minimise")
	public Path file;

	@Option(showDefaultValue = ALWAYS, names = {"-a", "--attempts"}, defaultValue = "1", description = "The number of tries before we give up looking for a divergence")
	public int attempts;

	@CommandLine.Parameters(showDefaultValue = ALWAYS, paramLabel = "<output>", description = "The file to write the minimised test case to")
	public Path output;

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
		System.out.println(tester.lookForMismatch(tc, attempts));

		var minimiser = new Minimiser(tester, attempts);
		tc = (RecordedTestCase)minimiser.minimise(tc);

		Files.writeString(output, tc.toXML());

		return null;
	}
}
