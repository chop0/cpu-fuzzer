package ax.xz.fuzz.runtime;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.blocks.BasicBlock;
import ax.xz.fuzz.tester.execution_result;
import com.github.icedland.iced.x86.Instruction;

import java.lang.foreign.*;

import static ax.xz.fuzz.tester.slave_h.*;
import static ax.xz.fuzz.tester.slave_h.munmap;

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
