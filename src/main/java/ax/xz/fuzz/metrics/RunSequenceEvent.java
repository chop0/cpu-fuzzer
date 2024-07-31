package ax.xz.fuzz.metrics;

import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

@Label("Sequence Run")
@Description("Execution of a sequence")
public class RunSequenceEvent extends Event {
	@TestSequenceEvent.TestSequenceId
	@Label("Test Sequence ID")
	public long id;

	@Label("Initial State")
	public String initialState;

	@Label("Final State")
	public String finalState;
}
