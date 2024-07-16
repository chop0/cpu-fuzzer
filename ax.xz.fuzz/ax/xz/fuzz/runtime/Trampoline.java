package ax.xz.fuzz.runtime;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static ax.xz.fuzz.runtime.MemoryUtils.Protection.*;
import static ax.xz.fuzz.runtime.MemoryUtils.alignUp;
import static ax.xz.fuzz.runtime.MemoryUtils.mmap;
import static ax.xz.fuzz.tester.slave_h.routine_begin$address;
import static ax.xz.fuzz.tester.slave_h.routine_end$address;

public record Trampoline(MemorySegment address) {
	private static final MemorySegment trampolineCode = routine_begin$address().reinterpret(routine_end$address().address() - routine_begin$address().address());

	public static long byteSize() {
		return trampolineCode.byteSize();
	}

	public MemorySegment relocate(MemorySegment functionAddress) {
		return address.asSlice(functionAddress.address() - routine_begin$address().address());
	}

	public static Trampoline create(Arena arena) {
		var trampoline = mmap(arena, MemorySegment.NULL, alignUp(trampolineCode.byteSize(), 4096), READ, WRITE, EXECUTE);

		trampoline.copyFrom(trampolineCode);

		return new Trampoline(trampoline);
	}
}
