package ax.xz.fuzz;

import java.lang.foreign.MemorySegment;
import java.util.random.RandomGenerator;

public record MemoryPartition(MemorySegment ms) {
	public long size() {
		return ms.byteSize();
	}

	private static long alignup(long value, long align) {
		long mask = align - 1;
		return (value + mask) & ~mask;
	}

	private static long aligndown(long value, long align) {
		long mask = align - 1;
		return value & ~mask;
	}

	private static boolean fitsIn(long value, int widthBytes) {
		return widthBytes >= 8 || value < (1L << (widthBytes * 8));
	}

	public boolean contains(long address, long size) {
		return address >= ms.address() && (address - ms.address()) < ms.byteSize() && (address + size) <= (ms.address() + ms.byteSize());
	}

	public long randomPosition(RandomGenerator r, int size, int align, int addressWidth) {
		var range = this.size() - size;

		if (!fitsIn(range, addressWidth))
			range &= (1L << (addressWidth * 8)) - 1;

		if (range < 0)
			throw new IllegalArgumentException("Size is larger than partition size");

		long result = (range == 0 ? 0 : r.nextLong(range) )+ ms.address();

		long alignedUp = alignup(result, align);
		long alignedDown = aligndown(result, align);

		if (fitsIn(alignedUp, addressWidth) && contains(alignedUp, size))
			return alignedUp;
		else if (fitsIn(alignedDown, addressWidth) && contains(alignedDown, size))
			return alignedDown;

		throw new AssertionError("Unreachable");
	}

	public boolean canFulfil(int size, int alignment, int addressWidth) {
		long alignedUp = alignup(ms.address(), alignment);
		long alignedDown = aligndown(ms.address(), alignedUp);

		return (fitsIn(alignedUp, addressWidth) && contains(alignedUp, size))
			   || (fitsIn(alignedDown, addressWidth) && contains(alignedDown, size));
	}

	public static MemoryPartition of(MemorySegment ms) {
		return new MemoryPartition(ms);
	}
}
