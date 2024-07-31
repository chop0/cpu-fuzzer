package ax.xz.fuzz.metrics;

import ax.xz.fuzz.blocks.InvarianceTestCase;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

@Label("Test Case Generation")
@Description("Generation of an invariant test case")
public class GenerationEvent extends Event {
	@TestSequenceEvent.TestSequenceId
	@Label("Test Sequence ID")
	public long id;

	@Label("Generated Test Case")
	public InvarianceTestCase testCase;

	@Label("Encoded size")
	public long encodedSize;

	@Label("Recorded Test Case")
	public String recordedTestCase;
}
