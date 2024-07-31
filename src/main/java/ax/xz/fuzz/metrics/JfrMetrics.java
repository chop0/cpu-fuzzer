package ax.xz.fuzz.metrics;

import ax.xz.fuzz.runtime.Config;
import ax.xz.fuzz.runtime.RecordedTestCase;
import ax.xz.fuzz.runtime.Tester;
import jdk.jfr.consumer.RecordedEvent;

public class JfrMetrics implements ThreadMetrics {
	private final FuzzThreadEvent event;

	public JfrMetrics(Tester tester, Config config) {
		this.event = new FuzzThreadEvent();
		if (!event.isEnabled())
			return;

		event.begin();
	}

	@Override
	public void recordThreadStart(Tester tester, Config config) {
		var event = new FuzzThreadEvent();

		if (!event.isEnabled())
			return;

		event.begin();

		event.trampolineLocation = tester.trampoline.address().address();
		event.branchLimit = config.branchLimit();
		event.maxInstructionCount = config.maxInstructionCount();
		event.blocksPerTestCase = config.blockCount();
		event.thread = Thread.currentThread();

		event.commit();
	}

	@Override
	public void recordRun(RecordedTestCase testCase) {
		ThreadMetrics.super.recordRun(testCase);
	}

	@Override
	public void recordEvent(RecordedEvent event) {
		ThreadMetrics.super.recordEvent(event);
	}
}
