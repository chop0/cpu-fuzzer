package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.StatusFlag;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.concurrent.*;

import static java.util.concurrent.ForkJoinPool.getCommonPoolParallelism;

public class Master {
	public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
		var config = Config.fromArgs(args);
		if (config.file() != null) {
			Triage.runFile(config.file());
			return;
		}

		try (var scope = Executors.newThreadPerTaskExecutor(Thread.ofPlatform().factory()); var metrics = new Metrics()) {
			metrics.startServer();

			for (int i = 0; i < config.threadCount(); i++) {
				int finalI = i;
				scope.submit(() -> {
					try {
						return lookForBug(finalI, metrics, config);
					} catch (InterruptedException e) {
						scope.shutdownNow();
						return 0;
					} catch (Throwable e) {
						e.printStackTrace();
						scope.shutdownNow();
						throw e;
					}
				});
			}

			scope.awaitTermination(1, TimeUnit.DAYS);
			scope.shutdownNow();
		}
	}

	private static final Object printLock = new Object();

	private static int lookForBug(int i, Metrics metrics, Config config) throws InterruptedException, FileNotFoundException {
		PrintWriter out = null;
		if (System.getenv("DEBUG") != null)
			out = new PrintWriter("output" + i + ".txt");
		try  {
			synchronized (printLock) {
				System.out.println("Starting thread " + i);
			}
			var tester = Tester.create(config, RegisterSet.ALL_EVEX, StatusFlag.all());

			while (!Thread.interrupted()) {
				var results = tester.runTest(out, false);
				if (results.getValue().isPresent()) {
					var xml = results.getValue().orElseThrow().toXML();
					var outputFile = new File("thread-%d-%d.xml".formatted(i, System.currentTimeMillis()));
					try (var writer = new FileWriter(outputFile)) {
						writer.write(xml);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}

			throw new InterruptedException();
		} finally {
			synchronized (printLock) {
				System.out.println("Exiting thread " + i);
			}
		}
	}
}
