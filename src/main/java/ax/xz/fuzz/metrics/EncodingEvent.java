package ax.xz.fuzz.metrics;

import ax.xz.fuzz.blocks.InvarianceTestCase;
import ax.xz.fuzz.runtime.RecordedTestCase;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

@Label("Sequence Encoding")
@Description("Encoding of a test sequence")
public class EncodingEvent extends Event {
	@TestSequenceEvent.TestSequenceId
	@Label("Test Sequence ID")
	public long id;

	@Label("Disassembled Code")
	public String code;

	@Label("Code Bytes")
	public String bytes;
}
