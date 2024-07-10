package ax.xz.fuzz.runtime;

import ax.xz.fuzz.tester.slave_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static ax.xz.fuzz.tester.slave_h.*;

public class MemoryUtils {
	public static MemorySegment mmap(Arena arena, MemorySegment address, long size, Protection... prot) {
		int protValue = 0;
		for (Protection p : prot) {
			protValue |= p.value;
		}

		var result = slave_h.mmap(address, size, protValue,
				MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0);

		if (result.address() == MAP_FAILED().address()) {
			throw new RuntimeException("mmap failed");
		}

		return result.reinterpret(size, arena, ms -> munmap(ms, size));
	}

	public static MemorySegment assignPkey(MemorySegment address, int pkey) {
		if (pkey_mprotect(address, address.byteSize(), PROT_READ() | PROT_WRITE() | PROT_EXEC(), pkey) != 0) {
			throw new RuntimeException("Failed to mprotect scratch1");
		}
		return address;
	}

	public static long alignUp(long value, long alignment) {
		return (value + alignment - 1) & ~(alignment - 1);
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
