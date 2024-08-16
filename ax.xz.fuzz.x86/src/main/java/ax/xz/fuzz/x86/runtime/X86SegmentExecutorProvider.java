package ax.xz.fuzz.x86.runtime;

import ax.xz.fuzz.runtime.SegmentExecutor;

public class X86SegmentExecutorProvider implements SegmentExecutor.Provider {
	static class InstanceHolder {
		static final X86SegmentExecutor INSTANCE = new X86SegmentExecutor();
	}

	@Override
	public SegmentExecutor nativeExecutor() {
		if (isAvailable()) {
			return InstanceHolder.INSTANCE;
		}

		throw new UnsupportedOperationException("X86 segment executor is not available");
	}

	@Override
	public boolean isAvailable() {
		return System.getProperty("os.arch").equals("x86_64") && System.getProperty("os.name").equals("Linux");
	}
}
