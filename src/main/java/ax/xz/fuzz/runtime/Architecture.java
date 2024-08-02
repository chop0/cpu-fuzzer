package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.BasicBlock;
import ax.xz.fuzz.blocks.Block;
import ax.xz.fuzz.blocks.BlockEntry;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.runtime.state.CPUState;
import com.github.icedland.iced.x86.asm.CodeAssembler;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.List;

import static ax.xz.fuzz.runtime.MemoryUtils.Protection.READ;
import static ax.xz.fuzz.runtime.MemoryUtils.Protection.WRITE;
import static ax.xz.fuzz.runtime.MemoryUtils.mmap;
import static com.github.icedland.iced.x86.asm.AsmRegisters.mem_ptr;
import static com.github.icedland.iced.x86.asm.AsmRegisters.zmmword_ptr;
import static com.github.icedland.iced.x86.asm.AsmRegistersZMM.zmm16;

public class Architecture {
	private static final boolean supportsEvex;

	static {
		try (var arena = Arena.ofConfined()) {
			var executor = SequenceExecutor.withIndex(Config.defaultConfig(), 0);
			var scratch = mmap(arena, MemorySegment.ofAddress(0x4000000), 4096, READ, WRITE);

			var assembler = new CodeAssembler(64);
			assembler.vmovups(zmm16, zmm16);

			var buf = ByteBuffer.allocate(15);
			assembler.assemble(buf::put, 0);
			buf.flip();
			var arr = new byte[buf.remaining()];
			buf.get(arr);

			Block[] blocks = { new BasicBlock(List.of(new BlockEntry.ConcreteEntry(arr))) };
			Branch[] branches = { new Branch(ExecutableSequence.BranchType.JA, 1, 1)};
			var result = executor.runSequence(CPUState.filledWith(scratch.address()), new ExecutableSequence(blocks, branches)).result();
			supportsEvex = result instanceof ExecutionResult.Success;
			System.out.println("Evex support: " + supportsEvex);
		}
	}

	public static boolean supportsEvex() {
		return supportsEvex;
	}

	public static RegisterSet supportedRegisters() {
		return (supportsEvex() ? RegisterSet.ALL_AVX512 : RegisterSet.ALL_AVX2);
	}
}
