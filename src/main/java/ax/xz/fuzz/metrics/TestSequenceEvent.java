package ax.xz.fuzz.metrics;

import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Label("Test Sequence")
@Description("A test case comprising two invariant sequences")
public class TestSequenceEvent extends Event {
	@TestSequenceId
	@Label("Test Sequence ID")
	public long id;

	@FuzzThreadEvent.FuzzThreadId
	@Label("Thread ID")
	public Thread thread;

	@Label("Recorded Test Case")
	public String recordedTestCase;

	@Label("Minimised Test Case")
	public String minimisedTestCase;

	@Label("Mismatch?")
	@Description("Whether the recorded test case and minimised test case differ")
	public boolean mismatch;

	@MetadataDefinition
	@Relational
	@Name("ax.xz.fuzz.TestSequenceId")
	@Label("Test Sequence ID")
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface TestSequenceId {
	}
}
