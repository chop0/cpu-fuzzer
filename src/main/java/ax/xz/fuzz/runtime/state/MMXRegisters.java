package ax.xz.fuzz.runtime.state;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;
import static java.nio.ByteOrder.nativeOrder;

public record MMXRegisters(long[] mm) {
	public MMXRegisters {
		if (mm.length != 8) {
			throw new IllegalArgumentException("MMX registers must have 8 elements");
		}
	}

	public List<RegisterDifference> diff(MMXRegisters other) {
		var differences = new ArrayList<RegisterDifference>();

		for (int i = 0; i < mm.length; i++) {
			if (mm[i] != other.mm[i]) {
				var bytesA = new byte[8];
				var bytesB = new byte[8];

				ByteBuffer.wrap(bytesA).order(nativeOrder()).putLong(mm[i]);
				ByteBuffer.wrap(bytesB).order(nativeOrder()).putLong(other.mm[i]);

				differences.add(new RegisterDifference("mm" + i, i, bytesA, bytesB));
			}
		}

		return differences;
	}

	public MMXRegisters withZeroed(int index) {
		var newMm = Arrays.copyOf(mm, mm.length);
		newMm[index] = 0;
		return new MMXRegisters(newMm);
	}

	static MMXRegisters filledWith(long thing) {
		var mm = new long[8];
		Arrays.fill(mm, thing);
		return new MMXRegisters(mm);
	}

	static MMXRegisters random(RandomGenerator rng) {
		var mm = new long[8];
		for (int i = 0; i < mm.length; i++) {
			mm[i] = rng.nextLong();
		}
		return new MMXRegisters(mm);
	}

	static MMXRegisters ofArray(MemorySegment array) {
		return new MMXRegisters(
			StreamSupport.stream(array.spliterator(JAVA_LONG_UNALIGNED), false)
				.mapToLong(ms -> ms.get(JAVA_LONG_UNALIGNED, 0))
				.toArray()
		);
	}

	void toArray(MemorySegment array) {
		for (int i = 0; i < mm.length; i++) {
			array.setAtIndex(JAVA_LONG, i, mm[i]);
		}
	}

	@Override
	public String toString() {
		return IntStream.range(0, mm.length)
			.mapToObj(i -> "mm" + i + "=" + Long.toUnsignedString(mm[i]))
			.reduce((a, b) -> a + ", " + b)
			.orElse("");
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof MMXRegisters mmx)) return false;
		return Arrays.equals(mm, mmx.mm);
	}
}
