package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.Block;
import ax.xz.fuzz.blocks.InterleavedBlock;
import ax.xz.fuzz.tester.execution_result;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Objects;
import java.util.function.BiFunction;

import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.Register.R15;

public class Minimiser {


	private static ExecutionResult run(ExecutableSequence tc) {
		try (var arena = Arena.ofConfined()) {
			var code = mmap(MemorySegment.NULL, 4096*16L, PROT_READ() | PROT_WRITE() | PROT_EXEC(), MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0)
					.reinterpret(4096 * 16, arena, ms -> munmap(ms, 4096 * 16L));

			var trampoline = Trampoline.create(arena);

			int length = tc.encode(code.address(), trampoline, code, R15, 100);

			var output = execution_result.allocate(arena);
			CPUState.filledWith(0).toSavedState(execution_result.state(output));
			do_test(trampoline.address(), code, length, output);

			return ExecutionResult.ofStruct(output);
		}
	}

	private static boolean mismatch(ExecutionResult a, ExecutionResult b) {
		return ExecutionResult.interestingMismatch(a, b);
	}
}
