package ax.xz.fuzz.runtime;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static ax.xz.fuzz.runtime.MemoryUtils.Protection.*;
import static ax.xz.fuzz.runtime.MemoryUtils.alignUp;
import static ax.xz.fuzz.runtime.MemoryUtils.mmap;
import static ax.xz.fuzz.tester.slave_h.*;

public record Trampoline(MemorySegment address) {
	private static final MemorySegment trampolineCode = routine_begin$segment().reinterpret(routine_end$segment().address() - routine_begin$segment().address());

	public static long byteSize() {
		return trampolineCode.byteSize();
	}

	public MemorySegment relocate(MemorySegment functionAddress) {
		return address.asSlice(functionAddress.address() - routine_begin$segment().address());
	}

	public static Trampoline create(Arena arena) {
		return create(arena, 0);
	}

	public static Trampoline create(Arena arena, long address) {
		var trampoline = mmap(arena, address == 0 ? MemorySegment.NULL : MemorySegment.ofAddress(address), alignUp(trampolineCode.byteSize(), 4096), READ, WRITE, EXECUTE);

		trampoline.copyFrom(trampolineCode);

		return new Trampoline(trampoline);
	}
}