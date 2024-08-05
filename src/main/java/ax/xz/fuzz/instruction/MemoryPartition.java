package ax.xz.fuzz.instruction;

import ax.xz.fuzz.blocks.NoPossibilitiesException;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static ax.xz.fuzz.blocks.ProgramRandomiser.MEMORY_GRANULARITY;
import static ax.xz.fuzz.runtime.MemoryUtils.*;
import static java.lang.foreign.MemoryLayout.sequenceLayout;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.util.Objects.requireNonNull;

public final record MemoryPartition(MemorySegment ms) {
	private static final MemoryPartition ADDRESS_SPACE = new MemoryPartition(MemorySegment.ofAddress(0).reinterpret(Long.MAX_VALUE));
	private static final MemoryPartition EMPTY = new MemoryPartition(MemorySegment.NULL);

	public static MemoryPartition empty() {
		return EMPTY;
	}

	public static MemoryPartition addressSpace64() {
		return ADDRESS_SPACE;
	}

	public static MemoryPartition of(MemorySegment ms) {
		return new MemoryPartition(ms);
	}

	public MemoryPartition {
		requireNonNull(ms);
	}

	public MemoryPartition() {
		this(MemorySegment.NULL);	
	}

	public boolean isEmpty() {
		return this == EMPTY || byteSize() == 0;
	}

	private static long[] sizeSum(MemorySegment[] ms) {
		long[] result = new long[ms.length];
		if (ms.length == 0)
			return result;

		result[0] = 0;
		for (int i = 1; i < ms.length; i++) {
			result[i] = result[i - 1] + ms[i - 1].byteSize();
		}

		return result;
	}

	private static MemorySegment[] coalesce(MemorySegment[] ms) {
		if (ms.length == 0)
			return ms;

		var result = new ArrayList<MemorySegment>();
		var current = ms[0];
		for (int i = 1; i < ms.length; i++) {
			var next = ms[i];
			if (current.address() + current.byteSize() == next.address()) {
				current = current.reinterpret(current.byteSize() + next.byteSize());
			} else {
				result.add(current);
				current = next;
			}
		}

		result.add(current);
		return result.toArray(MemorySegment[]::new);
	}

	public long byteSize() {
		return ms.byteSize();
	}

	private static boolean fitsIn(long value, int widthBytes) {
		return widthBytes >= 8 || value < (1L << (widthBytes * 8));
	}

	public boolean contains(long address, long size) {
		return address >= ms.address() && address + size <= ms.address() + ms.byteSize();
	}

	public long selectSegment(RandomGenerator r, int size, int align, int addressWidthBytes) throws NoPossibilitiesException {
		if (size == 0) size = 1;

		if (addressWidthBytes < 4 || size > MEMORY_GRANULARITY)
			throw new IllegalArgumentException();


		long index = r.nextLong(4) * align;
		return ms.address() + index;
	}

	public Stream<MemorySegment> segments(long size, long align, int addressWidth) {
		var layout = sequenceLayout(size == 0 ? 1 : size, JAVA_BYTE).withByteAlignment(align == 0 ? 1 : align);

		return Stream.of(ms)
			.filter(m -> m.byteSize() > layout.byteSize())
			.map(m -> alignUp(m, layout.byteAlignment()))
			.map(m -> m.asSlice(0, m.byteSize() / layout.byteSize() * layout.byteSize()))
			.flatMap(seg -> StreamSupport.stream(seg.spliterator(layout), false))
			.filter(seg -> fitsIn(seg.address(), addressWidth));
	}

	public boolean canFulfil(int size, int alignment, int addressWidth) {
		if (addressWidth < 4)
			throw new IllegalArgumentException();

		if (size == 0) size = 1;
		return size <= MEMORY_GRANULARITY && ms.byteSize() >= size;
	}

}
