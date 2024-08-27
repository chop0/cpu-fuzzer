package ax.xz.fuzz;

import ax.xz.fuzz.arch.Architecture;
import picocli.CommandLine;
import static picocli.CommandLine.*;
import static picocli.CommandLine.Model.*;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@CommandLine.Command(
	name = "cpu-fuzzer",
	description = "A CPU fuzzer",
	mixinStandardHelpOptions = true,
	subcommands = {MinimiseCommand.class, FuzzCommand.class, TriageCommand.class}
)
public class Master implements Runnable {
	@Spec public CommandSpec spec;

	@Override
	public void run() {
		throw new ParameterException(spec.commandLine(), "Missing required subcommand");
	}

	public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
		Architecture.withArchitecture(Architecture.nativeArchitecture(), () -> {
			System.exit(new CommandLine(new Master())
				.setExecutionStrategy(new RunLast())
				.setExecutionExceptionHandler((ex, _, _) -> {
					ex.printStackTrace();
					throw ex;
				})
				.execute(args));
		});
	}
}
