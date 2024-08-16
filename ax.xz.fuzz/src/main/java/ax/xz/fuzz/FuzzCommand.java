package ax.xz.fuzz;

import ax.xz.fuzz.blocks.InvarianceTestCase;
import ax.xz.fuzz.metrics.NetMetrics;
import ax.xz.fuzz.runtime.Config;
import ax.xz.fuzz.runtime.ExecutionResult;
import ax.xz.fuzz.runtime.Tester;
import picocli.CommandLine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.StructuredTaskScope;

import static ax.xz.fuzz.arch.Architecture.nativeArch;
import static ax.xz.fuzz.runtime.Config.defaultConfig;
import static java.nio.ByteOrder.nativeOrder;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Help.Visibility.ALWAYS;
import static picocli.CommandLine.Option;

@Command(description = "Fuzzes instructions continuously until interrupted", name = "fuzz", mixinStandardHelpOptions = true)
public class FuzzCommand implements Callable<Void> {
	private static final List<String> REGISTERS = List.of(
			"RAX",
			"RCX",
			"RDX",
			"RBX",
			"RSP",
			"RBP",
			"RSI",
			"RDI",
			"R8",
			"R9",
			"R10",
			"R11",
			"R12",
			"R13",
			"R14",
			"R15"
	);

	@Option(showDefaultValue = ALWAYS, names = {"-j", "--threads"}, description = "Number of threads to use")
	public int threadCount = ForkJoinPool.getCommonPoolParallelism();
	@Option(showDefaultValue = ALWAYS, names = {"-c", "--block-count"}, description = "Number of interleaved blocks each test case should contain.  Each interleaved block is a combination of two basic blocks.")
	public int blockCount = defaultConfig().blockCount();
	@Option(showDefaultValue = ALWAYS, names = {"-i", "--block-size"}, description = "Maximum number of instructions in each basic block")
	public int maxInstructionCount = defaultConfig().maxInstructionCount();
	@Option(showDefaultValue = ALWAYS, names = {"-b", "--branch-limit"}, description = "The number of branches a test case can take before it is forcibly terminated")
	public int branchLimit = defaultConfig().branchLimit();

	@Option(showDefaultValue = ALWAYS, names = {"--counter-register"}, defaultValue = "R15", description = "The register to reserve as a counter")
	public String counterRegister;

	@CommandLine.Parameters(paramLabel = "<output directory>", index = "0", description = "The directory to put discovered test cases")
	public String outputDirectory;

	@Option(showDefaultValue = ALWAYS, names = {"-p", "--metrics-port"}, defaultValue = "9100", description = "The port to expose metrics on")
	public int metricsPort;

	@Option(showDefaultValue = ALWAYS, names = {"--metrics-host"}, defaultValue = "0.0.0.0", description = "The host to expose metrics on")
	public String metricsHost;

	@Option(showDefaultValue = ALWAYS, names = {"-M", "--no-metrics"}, description = "Disable embedded metrics server")
	public boolean noMetrics;

	@Override
	public Void call() throws IOException, ExecutionException {
		// TODO: configure mutators from command line
		var config = new Config(nativeArch().allMutators(), threadCount, blockCount, maxInstructionCount, branchLimit,  nativeArch().defaultCounter());

		var host = new InetSocketAddress(metricsHost, metricsPort);

		try (var metrics = new NetMetrics(host); var sts = new StructuredTaskScope.ShutdownOnFailure("Fuzzing", Thread.ofPlatform().factory())) {
			if (!noMetrics) {
				metrics.startServer();
			}

			for (int i = 0; i < config.threadCount(); i++) {
				sts.fork(() -> lookForBug(config, metrics));
			}

			sts.join();
			sts.throwIfFailed();
		} catch (InterruptedException e) {
		} catch (ExecutionException e) {
			e.printStackTrace();
			throw e;
		}
		return null;
	}

	private int lookForBug(Config config, NetMetrics metrics) throws InterruptedException, IOException {
		Tester t = Tester.create(config);
		var outputDirectory = Path.of(this.outputDirectory);

		var minimisedDirectory = outputDirectory.resolve("minimised");
		var rawDirectory = outputDirectory.resolve("raw");

		Files.createDirectories(minimisedDirectory);
		Files.createDirectories(rawDirectory);

		while (!Thread.interrupted()) {
			var results = t.runTest();
			
			recordResults(config, metrics, results.a());
			recordResults(config, metrics, results.b());

			if (results.hasInterestingMismatch()) {
				if (results.a() instanceof ExecutionResult.Success(var A) && results.b() instanceof ExecutionResult.Success(var B)) {
					System.out.println(A.diff(B));
				}
				metrics.incrementMismatch();
				var time = System.currentTimeMillis();
				var xml = t.record(results.tc()).toXML();

				Files.writeString(rawDirectory.resolve("bug-%d.xml".formatted(time)), xml);

				var tc = t.minimise(results.tc());
				var minimalXml = t.record((InvarianceTestCase) tc).toXML();
				Files.writeString(minimisedDirectory.resolve("bug-minimised-%d.xml".formatted(time)), minimalXml);
			}
		}

		return 0;
	}

	private void recordResults(Config config, NetMetrics metrics, ExecutionResult result) {
		if (result instanceof ExecutionResult.Success a) {
			metrics.incNumSamples(false);
			metrics.incrementBranches(getCounterValue(config, a));
		} else if (result instanceof ExecutionResult.Fault a) {
			metrics.incNumSamples(true);
			metrics.incFaultType(a.getClass().getSimpleName());
			if (a instanceof ExecutionResult.Fault.Sigalrm) metrics.incrementAlarm();
		}
	}

	private long getCounterValue(Config config, ExecutionResult.Success a) {
		var bytes = a.state().values().get(config.counterRegister());
		var bb = ByteBuffer.allocate(bytes.length).order(nativeOrder()).put(bytes).flip();

		return switch (bb.remaining()) {
			case 1 -> bb.get() & 0xFF;
			case 2 -> bb.getShort() & 0xFFFF;
			case 4 -> bb.getInt() & 0xFFFFFFFFL;
			case 8 -> bb.getLong() & 0xFFFFFFFFFFFFFFFFL;
			default -> throw new IllegalStateException("Unexpected value: " + bb.remaining());
		};
	}
}
