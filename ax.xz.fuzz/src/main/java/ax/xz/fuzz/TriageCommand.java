package ax.xz.fuzz;

import ax.xz.fuzz.runtime.Triage;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.zip.DataFormatException;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Help.Visibility.ALWAYS;
import static picocli.CommandLine.Option;

@Command(description = "Attempts to reproduce a test case", name = "reproduce", mixinStandardHelpOptions = true)
public class TriageCommand implements Callable<Void> {
	@CommandLine.Parameters(index = "0", description = "The file to run")
	public Path file;

	@Option(showDefaultValue = ALWAYS, names = {"-a", "--attempts"}, defaultValue = "1", description = "The number of tries before we give up looking for a divergence")
	public int attempts;


	@Override
	public Void call() throws IOException, DataFormatException {
		Triage.runFile(file, attempts);
		return null;
	}
}
