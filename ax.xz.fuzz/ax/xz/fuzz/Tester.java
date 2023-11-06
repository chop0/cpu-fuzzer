package ax.xz.fuzz;

import ax.xz.fuzz.tester.execution_result;
import ax.xz.fuzz.tester.slave_h;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.asm.CodeAssembler;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import static ax.xz.fuzz.tester.slave_h.do_test;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

public class Tester {
	public static ExecutionResult runBlock(CPUState startState, CombinedBlock block) throws CombinedBlock.UnencodeableException {
		try (var arena = Arena.ofConfined()) {
			var code = arena.allocate(block.instructions().length * 15L);
			int[] locations = block.encode(code);

			var output = execution_result.allocate(arena);
			startState.toSavedState(execution_result.state$slice(output));
			do_test(code, locations.length, output);

			return ExecutionResult.ofStruct(output);
		}
	}

	public static ExecutionResult runBlock(CPUState startState, Opcode[] opcodes, Instruction[] instructions) throws CombinedBlock.UnencodeableException {
		try (var arena = Arena.ofConfined()) {
			var code = arena.allocate(instructions.length * 15L);
			int[] locations = CombinedBlock.encode(code, opcodes, instructions);

			var output = execution_result.allocate(arena);
			startState.toSavedState(execution_result.state$slice(output));
			do_test(code, locations.length, output);

			return ExecutionResult.ofStruct(output);
		}
	}

	public static ExecutionResult runBlock(CPUState startState, MemorySegment code) throws CombinedBlock.UnencodeableException {
		try (var arena = Arena.ofConfined()) {

			var output = execution_result.allocate(arena);
			startState.toSavedState(execution_result.state$slice(output));
			do_test(code, code.byteSize(), output);

			return ExecutionResult.ofStruct(output);
		}
	}

	public static Opcode findInstructionOpcode(CombinedBlock block, long faultOffset) throws CombinedBlock.UnencodeableException {
		try (var arena = Arena.ofConfined()) {
			var code = arena.allocate(4096*16, 4096);
			int[] locations = block.encode(code);

			if (faultOffset >= locations.length)
				return null;

			return block.opcodes()[locations[(int) faultOffset]];
		}
	}
}
