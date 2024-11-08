package ax.xz.fuzz.mman;

import ax.xz.fuzz.mman.gen.MmanNative;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static ax.xz.fuzz.mman.gen.MmanNative.*;

public class MemoryUtils {
	private static final int MAP_ANONYMOUS = switch (System.getProperty("os.arch")) {
		case "riscv64" -> 0x20;
		case "x86_64" -> 4096;
		default -> throw new UnsupportedOperationException("Unsupported architecture");
	};

	public static MemorySegment mmap(Arena arena, MemorySegment address, long size, Protection... prot) {
		int protValue = 0;
		for (Protection p : prot) {
			protValue |= p.value;
		}

		int flags = MAP_PRIVATE() | MAP_ANONYMOUS;
		if (address != MemorySegment.NULL) {
			flags |= MAP_FIXED();
		}

		var result = MmanNative.mmap(address, size, protValue,
				flags, -1, 0);

		if (result.address() == MAP_FAILED().address()) {
			throw new RuntimeException("mmap failed");
		}

		return result.reinterpret(size, arena, ms -> munmap(ms, size));
	}

	public static long alignUp(long value, long alignment) {
		return (value + alignment - 1) & ~(alignment - 1);
	}

	public static MemorySegment alignUp(MemorySegment ms, long alignment) {
		long address = ms.address();
		long aligned = alignUp(address, alignment);
		long diff = aligned - address;
		return ms.asSlice(diff, ms.byteSize() - diff);
	}

	public static long alignDown(long value, long align) {
		return value & ~(align - 1);
	}

	public static MemorySegment alignDown(MemorySegment ms, long alignment) {
		long address = ms.address();
		long aligned = alignDown(address, alignment);
		long diff = address - aligned;
		return ms.asSlice(0, ms.byteSize() - diff);
	}

	public static long unsignedMin(long a, long b) {
		return Long.compareUnsigned(a, b) < 0 ? a : b;
	}

	public enum Protection {
		READ(PROT_READ()),
		WRITE(PROT_WRITE()),
		EXECUTE(PROT_EXEC());

		private final int value;

		Protection(int value) {
			this.value = value;
		}
	}
}
