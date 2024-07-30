package ax.xz.fuzz.runtime;

import ax.xz.fuzz.tester.execution_result;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.MemoryLayout.*;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public class Command {
	public static final int SHUTDOWN = 1;
	public static final int METRICS = 2;
	public static final int RESULTS = 3;

	public static final MemoryLayout LAYOUT = structLayout(
			JAVA_BYTE.withName("type"),
			unionLayout(
					structLayout().withName("shutdown"),
					sequenceLayout(0, execution_result.layout()).withName("results")
			)
	);

	private static final VarHandle type$VH = LAYOUT.varHandle(groupElement("type"));
	private static final VarHandle task$length$VH = LAYOUT.varHandle(groupElement("task"), groupElement("length"));
	private static final VarHandle task$data$VH = LAYOUT.varHandle(groupElement("task"), groupElement("data"));

	public static byte type(MemorySegment segment) {
		return (byte) type$VH.get(segment);
	}

	public static void type(MemorySegment segment, byte value) {
		type$VH.set(segment, value);
	}

	public static int task$length(MemorySegment segment) {
		return (int) task$length$VH.get(segment);
	}

	public static void task$length(MemorySegment segment, int value) {
		task$length$VH.set(segment, value);
	}

	public static MemorySegment task$data(MemorySegment segment) {
		return (MemorySegment) task$data$VH.get(segment);
	}

	public static void task$data(MemorySegment segment, MemorySegment value) {
		task$data$VH.set(segment, value);
	}
}
