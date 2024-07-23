package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.StatusFlag;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.*;

import static java.util.concurrent.ForkJoinPool.getCommonPoolParallelism;

public class Master {
	public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
		try (var scope = Executors.newThreadPerTaskExecutor(Thread.ofPlatform().factory()); var metrics = new Metrics()) {
			metrics.startServer();
			scope.submit(() -> lookForBug(0, metrics));
			Thread.sleep(2000); // race
			int threadCount = System.getenv("THREAD_COUNT") == null ? getCommonPoolParallelism() : Integer.parseInt(System.getenv("THREAD_COUNT"));

			for (int i = 1; i < threadCount; i++) {
				int finalI = i;
				scope.submit(() -> lookForBug(finalI, metrics));
			}

			scope.awaitTermination(1, TimeUnit.DAYS);
			scope.shutdownNow();
		}
	}

	private static final Object printLock = new Object();

	private static int lookForBug(int i, Metrics metrics) throws InterruptedException {
		try  {
			synchronized (printLock) {
				System.out.println("Starting thread " + i);
			}
			var tester = Tester.create(RegisterSet.ALL_EVEX, StatusFlag.all());

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

			throw new InterruptedException();
		} catch (Throwable e) {
			e.printStackTrace();
			return 1;
		} finally {
			synchronized (printLock) {
				System.out.println("Exiting thread " + i);
			}
		}
	}
}
