package ax.xz.fuzz.runtime;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static ax.xz.fuzz.runtime.MemoryUtils.Protection.*;
import static ax.xz.fuzz.runtime.MemoryUtils.alignUp;
import static ax.xz.fuzz.runtime.MemoryUtils.mmap;
import static ax.xz.fuzz.runtime.Tester.SCRATCH_PKEY;
import static ax.xz.fuzz.tester.slave_h.*;

public record Trampoline(MemorySegment address) {
	private static final MemorySegment trampolineCode = routine_begin$address().reinterpret(routine_end$address().address() - routine_begin$address().address());

	public MemorySegment relocate(MemorySegment functionAddress) {
		return address.asSlice(functionAddress.address() - routine_begin$address().address());
	}

	public static Trampoline create(Arena arena) {
		var trampoline = mmap(arena, MemorySegment.NULL, alignUp(trampolineCode.byteSize(), 4096), READ, WRITE, EXECUTE);
		pkey_mprotect(trampoline, trampoline.byteSize(), PROT_READ() | PROT_WRITE() | PROT_EXEC(), SCRATCH_PKEY);
		trampoline.copyFrom(trampolineCode);

		return new Trampoline(trampoline);
	}
}
