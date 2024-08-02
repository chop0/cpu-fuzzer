package ax.xz.fuzz;

import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.StatusFlag;
import ax.xz.fuzz.metrics.NetMetrics;
import ax.xz.fuzz.runtime.*;
import picocli.CommandLine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.zip.DataFormatException;

import static com.github.icedland.iced.x86.Register.RAX;

public class Master {
	private static final Object printLock = new Object();

	public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
		var config = new CommandLine(Config.class);
		config.setExecutionStrategy(result -> {
			if (result.isUsageHelpRequested()) {
				config.usage(System.out);
				return 0;
			}

			try {
				var cfg = config.<Config>getCommand();
				if (cfg.file().isPresent()) {
					Triage.runFile(cfg.file().get());
					return 0;
				} else {
					continuousRun(config, cfg);
					return 0;
				}
			} catch (InterruptedException e) {
				return 0;
			} catch (IOException | DataFormatException e) {
				throw new CommandLine.ExecutionException(config, e.getMessage(), e);
			}
		});
		System.exit(config.execute(args));
	}

	private static void continuousRun(CommandLine cmd, Config config) throws InterruptedException {
		try (var metrics = new NetMetrics().startServer(); var scope = new StructuredTaskScope.ShutdownOnFailure("Fuzzing", Thread.ofPlatform().factory())) {
			for (int i = 0; i < config.threadCount(); i++) {
				int finalI = i;
				scope.fork(() -> lookForBug(finalI, config, metrics));
			}

			scope.join();
			scope.throwIfFailed();
		} catch (ExecutionException e) {
			throw new CommandLine.ExecutionException(cmd, e.getMessage(), e);
		}
	}

	private static int lookForBug(int i, Config config, NetMetrics metrics) throws InterruptedException {
		Tester t = Tester.create(config);

		while (!Thread.interrupted()) {
			var results = t.runTest();

			recordResults(config, metrics, results.a());
			recordResults(config, metrics, results.b());

			if (results.hasInterestingMismatch()) {
				metrics.incrementMismatch();
				var xml = t.record(results.tc()).toXML();
				var outputFile = new File("thread-%d-%d.xml".formatted(i, System.currentTimeMillis()));
				try (var writer = new FileWriter(outputFile)) {
					writer.write(xml);
				} catch (IOException e) {
					e.printStackTrace();
				}

				var minimal = t.minimise(results.tc());
				var minimalXml = t.record(minimal).toXML();
				var minimalOutputFile = new File("thread-%d-%d-minimal.xml".formatted(i, System.currentTimeMillis()));
				try (var writer = new FileWriter(minimalOutputFile)) {
					writer.write(minimalXml);
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		}

		throw new InterruptedException();
	}

	private static void recordResults(Config config, NetMetrics metrics, ExecutionResult result) {
		if (result instanceof ExecutionResult.Success a) {
			metrics.incNumSamples(false);
			metrics.incrementBranches(a.state().gprs().values()[14]);
		} else if (result instanceof ExecutionResult.Fault a) {
			metrics.incNumSamples(true);
			if (a instanceof ExecutionResult.Fault.Sigalrm)
				metrics.incrementAlarm();
		}
	}
}
