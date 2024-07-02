package ax.xz.fuzz;

import ax.xz.fuzz.tester.execution_result;
import ax.xz.fuzz.tester.slave_h;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.asm.CodeAssembler;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import static ax.xz.fuzz.tester.slave_h.*;
import static ax.xz.fuzz.tester.slave_h.munmap;
import static com.github.icedland.iced.x86.Register.R15;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

public class Tester {
	public static ExecutionResult runBlock(CPUState startState, Opcode[] opcodes, Instruction[] instructions) throws BasicBlock.UnencodeableException {
		try (var arena = Arena.ofConfined()) {
			var code = mmap(MemorySegment.NULL, (instructions.length + 1) * 15L, PROT_READ() | PROT_WRITE() | PROT_EXEC(), MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0)
					.reinterpret(4096 * 16, arena, ms -> munmap(ms, (instructions.length + 1) * 15L));

			int[] locations = BasicBlock.encode(code, opcodes, instructions);

			var output = execution_result.allocate(arena);
			startState.toSavedState(execution_result.state(output));
			do_test(code, locations.length, output);

			return ExecutionResult.ofStruct(output);
		}
	}

	public static ExecutionResult runBlock(CPUState startState, MemorySegment code) {
		try (var arena = Arena.ofConfined()) {

			var output = execution_result.allocate(arena);
			startState.toSavedState(execution_result.state(output));
			do_test(code, code.byteSize(), output);

			return ExecutionResult.ofStruct(output);
		}
	}

}
