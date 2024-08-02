package ax.xz.fuzz.runtime.state;

import ax.xz.fuzz.tester.saved_state;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

import static com.github.icedland.iced.x86.Register.*;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.nio.ByteOrder.nativeOrder;

public record GeneralPurposeRegisters(long[] values) {
	public static final int[] constituents = {
		RAX, RBX, RCX, RDX, RSI, RDI, RBP, R8, R9, R10, R11, R12, R13, R14, R15, RSP
	};
	public static final String[] names = {
		"rax", "rbx", "rcx", "rdx", "rsi", "rdi", "rbp", "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15", "rsp"
	};

	private static final VarHandle[] structAccessors;

	static {
		structAccessors = new VarHandle[constituents.length];
		for (int i = 0; i < constituents.length; i++) {
			structAccessors[i] = saved_state.layout().varHandle(groupElement(names[i]));
		}
	}

	public static String name(int idx) {
		return names[idx];
	}

	public GeneralPurposeRegisters {
		if (values.length != constituents.length) {
			throw new IllegalArgumentException("Expected " + constituents.length + " values, got " + values.length);
		}
	}

	public List<RegisterDifference> diff(GeneralPurposeRegisters other) {
		var differences = new ArrayList<RegisterDifference>();
		for (int i = 0; i < values.length; i++) {
			if (values[i] != other.values[i]) {
				var bytesA = new byte[8];
				var bytesB = new byte[8];

				ByteBuffer.wrap(bytesA).order(nativeOrder()).putLong(values[i]);
				ByteBuffer.wrap(bytesB).order(nativeOrder()).putLong(other.values[i]);

				differences.add(new RegisterDifference(names[i], constituents[i], bytesA, bytesB));
			}
		}
		return differences;
	}

	public GeneralPurposeRegisters withZeroed(int idx) {
		return with(idx, 0L);
	}

	public GeneralPurposeRegisters with(int idx, long value) {
		var newValues = Arrays.copyOf(values, values.length);
		newValues[idx] = value;
		return new GeneralPurposeRegisters(newValues);
	}

	public GeneralPurposeRegisters withRsp(long value) {
		return with(15, value);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof GeneralPurposeRegisters that)) return false;
		if (values == that.values) return true;
		if (values.length != that.values.length) return false;

		return Arrays.equals(values, that.values);
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			sb.append(names[i]).append(": ").append(String.format("0x%016x", values[i])).append("\n");
		}
		return sb.toString();
	}

	void toSavedState(MemorySegment savedState) {
		for (int i = 0; i < constituents.length; i++) {
			structAccessors[i].set(savedState, 0, values[i]);
		}
	}

	static GeneralPurposeRegisters filledWith(long thing) {
		var values = new long[constituents.length];
		Arrays.fill(values, thing);
		return new GeneralPurposeRegisters(values);
	}

	static GeneralPurposeRegisters random(RandomGenerator rng) {
		var values = new long[constituents.length];
		Arrays.setAll(values, i -> rng.nextLong());
		return new GeneralPurposeRegisters(values);
	}

	static GeneralPurposeRegisters ofSavedState(MemorySegment savedState) {
		var values = new long[constituents.length];
		for (int i = 0; i < constituents.length; i++) {
			values[i] = (long) structAccessors[i].get(savedState, 0L);
		}

		return new GeneralPurposeRegisters(values);
	}
}
