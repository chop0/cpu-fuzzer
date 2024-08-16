package ax.xz.fuzz.runtime;

import ax.xz.fuzz.arch.CPUState;
import ax.xz.fuzz.instruction.RegisterSet;

import java.lang.foreign.MemorySegment;
import java.util.ServiceLoader;

public interface SegmentExecutor {
	static SegmentExecutor nativeExecutor() {
		var loader = ServiceLoader.load(Provider.class);

		for (var provider : loader) {
			if (provider.isAvailable())
				return provider.nativeExecutor();
		}

		throw new UnsupportedOperationException("No available segment executor");
	}

	ExecutionResult runCode(MemorySegment code, CPUState initialState);
	long okExitAddress();

	interface Provider {
		SegmentExecutor nativeExecutor();
		boolean isAvailable();
	}
}
