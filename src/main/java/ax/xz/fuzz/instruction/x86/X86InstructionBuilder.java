package ax.xz.fuzz.instruction.x86;

import ax.xz.fuzz.instruction.InstructionBuilder;
import ax.xz.fuzz.instruction.RegisterSet;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.OpKind;
import com.github.icedland.iced.x86.asm.CodeAssembler;

import java.nio.ByteBuffer;

public record X86InstructionBuilder(Instruction instruction) implements InstructionBuilder {
	public byte[] encode(long pc) {
		class inner {
			private static final ThreadLocal<CodeAssembler> assembler = ThreadLocal.withInitial(() -> new CodeAssembler(64));
		}
		var instructionAssembler = inner.assembler.get();

		byte[] result = new byte[32];
		var buf = ByteBuffer.wrap(result);
		instructionAssembler.reset();
		instructionAssembler.addInstruction(instruction);
		instructionAssembler.assemble(buf::put, pc);
		var trimmed = new byte[buf.position()];
		System.arraycopy(result, 0, trimmed, 0, trimmed.length);

		return trimmed;
	}
}
