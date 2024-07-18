package ax.xz.fuzz.instruction;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.blocks.randomisers.ReverseRandomGenerator;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static ax.xz.fuzz.runtime.MemoryUtils.*;
import static java.lang.foreign.MemoryLayout.sequenceLayout;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.util.Objects.requireNonNull;

public record MemoryPartition(long[] offsets, MemorySegment... ms) {
	private static final MemoryPartition ADDRESS_SPACE = new MemoryPartition(MemorySegment.ofAddress(0).reinterpret(Long.MAX_VALUE));
	private static final MemoryPartition EMPTY = new MemoryPartition(new long[0], new MemorySegment[0]);

	public static MemoryPartition empty() {
		return EMPTY;
	}

	public static MemoryPartition addressSpace64() {
		return ADDRESS_SPACE;
	}

	public MemoryPartition {
		requireNonNull(ms);

		Arrays.sort(ms, Comparator.comparingLong(MemorySegment::address));
		ms = coalesce(ms);
		offsets = sizeSum(ms);
	}

	public MemoryPartition(MemorySegment... ms) {
		this(null, ms);
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
				current = current.reinterpret( current.byteSize() + next.byteSize());
			} else {
				result.add(current);
				current = next;
			}
		}

		result.add(current);
		return result.toArray(MemorySegment[]::new);
	}

	public long byteSize() {
		long result = 0;
		for (MemorySegment m : ms)
			result += m.byteSize();

		return result;
	}

	private static boolean fitsIn(long value, int widthBytes) {
		return widthBytes >= 8 || value < (1L << (widthBytes * 8));
	}

	public boolean contains(long address, long size) {
		var segment = findSubsegment(address);
		if (segment == null)
			return false;

		return segment.byteSize() >= size;
	}

	private int findSubsegmentIndex(long address) {
		int nearest = Arrays.binarySearch(ms, MemorySegment.ofAddress(address), Comparator.comparingLong(MemorySegment::address));
		if (nearest >= 0)
			return nearest;
		else {
			int segment = -(nearest + 1) - 1;
			if (segment < 0 || segment >= ms.length)
				return -1;

			return segment;
		}
	}

	private MemorySegment findSubsegment(long address) {
		int index = findSubsegmentIndex(address);
		if (index < 0)
			return null;

		if (address - ms[index].address() >= ms[index].byteSize())
			return null;

		return ms[index].asSlice(address - ms[index].address());
	}

	private MemorySegment findOffset(long offset) {
		int nearest = Arrays.binarySearch(offsets, offset);
		if (nearest >= 0)
			return ms[nearest];
		else {
			int segment = -(nearest + 1) - 1;
			if (segment < 0 || segment >= ms.length)
				return null;

			long segmentOffset = offset - offsets[segment];
			return ms[segment].asSlice(segmentOffset);
		}
	}

	public long selectSegment(RandomGenerator r, int size, int align, int addressWidthBytes) throws NoPossibilitiesException {
		if (size == 0) size = 1;
		long position = r.nextLong(byteSize() / size) * size;
		var result = findOffset(position);
		if (result == null)
			throw new NoPossibilitiesException();

		return result.address();
	}

	public void reverseSegment(ReverseRandomGenerator random, int requiredSize, int alignment, int addressWidthBytes, long outcome) throws NoPossibilitiesException {
		long start = ms[0].address();
		long end = unsignedMin(ms[ms.length - 1].address() + ms[ms.length - 1].byteSize(), (1L << (addressWidthBytes * 8)) - 1);
		if (Long.compareUnsigned(start + requiredSize, end) > 0)
			throw new NoPossibilitiesException();

		int index = findSubsegmentIndex(outcome);
		if (index < 0)
			throw new NoPossibilitiesException();

		long segmentOffset = outcome - ms[index].address();
		long overallOffset = offsets[index] + segmentOffset;
		random.pushLong(overallOffset);
	}

	public Stream<MemorySegment> segments(int size, int align, int addressWidth) {
		var layout = sequenceLayout(size == 0 ? 1 : size, JAVA_BYTE).withByteAlignment(align == 0 ? 1 : align);

		return Stream.of(ms)
			.filter(m -> m.byteSize() > layout.byteSize())
			.map(m -> alignUp(m, layout.byteAlignment()))
			.map(m -> m.asSlice(0, m.byteSize() / layout.byteSize() * layout.byteSize()))
			.flatMap(seg -> StreamSupport.stream(seg.spliterator(layout), false))
			.filter(seg -> fitsIn(seg.address(), addressWidth));
	}

	public boolean canFulfil(int size, int alignment, int addressWidth) {
		return segments(size, alignment, addressWidth).findAny().isPresent();
	}

	public MemoryPartition union(MemoryPartition other) {
		var result = new MemorySegment[ms.length + other.ms.length];
		System.arraycopy(ms, 0, result, 0, ms.length);
		System.arraycopy(other.ms, 0, result, ms.length, other.ms.length);

		return new MemoryPartition(result);
	}

	public static MemoryPartition of(MemorySegment... ms) {
		return new MemoryPartition(ms);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (MemoryPartition) obj;
		return Arrays.equals(this.offsets, that.offsets) && Arrays.equals(this.ms, that.ms);
	}
}
