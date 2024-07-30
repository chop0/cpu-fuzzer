package ax.xz.fuzz.runtime;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

public record Config(int threadCount, int blockCount, int maxInstructionCount, Path file) {
	private static final Map<String, String> shorthands = Map.of(
		"h", "help",
		"j", "threads",
		"b", "block-count",
		"i", "block-size",
		"f", "file"
	);
	private static final Set<String> validKeys = Set.of("help", "threads", "block-count", "block-size", "file");

	public static Config defaultConfig() {
		return new Config(ForkJoinPool.getCommonPoolParallelism(), 2, 10, null);
	}

	public static Config fromArgs(String[] args) {
		var cmdlineArgs = parseArgs(args);
		if (cmdlineArgs.containsKey("help") || cmdlineArgs.keySet().stream().anyMatch(n -> !validKeys.contains(n))) {
			printHelp();
		}

		int threads = Integer.parseInt(cmdlineEnvDefault(cmdlineArgs, "threads", "THREAD_COUNT", String.valueOf(ForkJoinPool.getCommonPoolParallelism())));
		int blockCount = Integer.parseInt(cmdlineEnvDefault(cmdlineArgs, "block-count", "NUM_BLOCKS", "2"));
		int maxInstructionCount = Integer.parseInt(cmdlineEnvDefault(cmdlineArgs, "block-size", "MAX_INSTRUCTIONS", "10"));
		var loc = cmdlineEnvDefault(cmdlineArgs, "file", "FILE", null);
		Path file = loc != null ? Path.of(loc) : null;
		return new Config(threads, blockCount, maxInstructionCount, file);
	}

	private static void printHelp() {
		System.err.println("""
			Usage: fuzz [options]
			Options:
			 	--help | -h
			 	--file <path> | -f <path> | FILE=<path>
			 	--threads <count> | -j <count> | THREAD_COUNT=<count>
			 	--block-count <count> | -b <count> | NUM_BLOCKS=<count>
			 	--block-size <count> | -i <count> | MAX_INSTRUCTIONS=<count>""");
		System.exit(1);
	}

	private static String cmdlineEnvDefault(Map<String, String> args, String key, String env, String def) {
		return Optional.ofNullable(args.get(key)).or(() -> Optional.ofNullable(System.getenv(env))).orElse(def);
	}

	private static Map<String, String> parseArgs(String[] args) {
		var map = new HashMap<String, String>();
		for (int i = 0; i < args.length; i++) {
			var key =  args[i];
			if (key.startsWith("--")) {
				String value = "";
				if (key.contains("="))
					value = key.substring(key.indexOf('=') + 1);
				else if (i + 1 < args.length)
					value = args[++i];
				map.put(key.substring(2), value);
			} else if (key.startsWith("-")) {
				String value = i + 1 < args.length ? args[++i] : "";
				map.put(shorthands.get(key.substring(1)), value);
			} else {
				throw new IllegalArgumentException("Invalid argument: " + args[i]);
			}
		}

		return map;
	}
}
