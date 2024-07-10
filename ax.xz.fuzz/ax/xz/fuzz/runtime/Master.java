package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.BlockGenerator;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadPoolExecutor;

import static java.util.concurrent.ForkJoinPool.getCommonPoolParallelism;

public class Master {
	public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
		try (var scope = new StructuredTaskScope.ShutdownOnFailure(); var metrics = new Metrics()) {
			metrics.startServer();
			scope.fork(() -> lookForBug(0, metrics));
			Thread.sleep(200); // race
			int threadCount = System.getenv("THREAD_COUNT") == null ? getCommonPoolParallelism() : Integer.parseInt(System.getenv("THREAD_COUNT"));

			for (int i = 1; i < threadCount; i++) {
				int finalI = i;
				scope.fork(() -> lookForBug(finalI, metrics));
			}

			scope.join();
			scope.throwIfFailed();
		}
	}

	private static int lookForBug(int i, Metrics metrics) throws InterruptedException {
		try  {
			var tester = new Tester(i);

			while (!Thread.interrupted()) {
				var results = tester.runTest();

				ExecutionResult previous = null;

				for (ExecutionResult result : results) {
					if (ExecutionResult.interestingMismatch(previous, result)) {
						System.out.println("Mismatched results");
						System.out.println("Previous: " + previous);
						System.out.println("Current: " + result);
						System.out.println("PID: " + ManagementFactory.getRuntimeMXBean().getName());
						System.exit(1);
					}

					metrics.incNumSamples(result instanceof ExecutionResult.Fault);
					if (result instanceof ExecutionResult.Success success)
						metrics.incrementBranches(100 - success.state().gprs().r15());

					previous = result;
				}
			}

			return 0;
		} catch (BlockGenerator.NoPossibilitiesException e) {
			throw new RuntimeException(e);
		}
	}
}
