package ax.xz.fuzz.metrics;

import ax.xz.fuzz.runtime.Config;
import ax.xz.fuzz.runtime.RecordedTestCase;
import ax.xz.fuzz.runtime.Tester;
import jdk.jfr.consumer.RecordedEvent;

public interface ThreadMetrics {
	default void recordThreadStart(Tester tester, Config config) {}
	default void recordRun(RecordedTestCase testCase) {};
	default void recordEvent(RecordedEvent event) {};

	ThreadLocal<ThreadMetrics> current = InheritableThreadLocal.withInitial(DefaultMetrics::new);
	static ThreadMetrics current() {
		return current.get();
	}

	class DefaultMetrics implements ThreadMetrics {

	 }
}
