package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.mutate.DeferredMutation;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.asm.CodeAssembler;
import com.github.icedland.iced.x86.enc.EncoderException;

import java.nio.ByteBuffer;
import java.util.Collection;

public sealed interface BlockEntry {
	static BlockEntry nop1() {
		return new ConcreteEntry(new byte[]{(byte) 0x90});
	}
	static BlockEntry nop3() {
		return new ConcreteEntry(new byte[]{(byte) 0x0f, (byte) 0x1f, (byte) 0x00});
	}

	byte[] encode(long rip) throws Block.UnencodeableException;

	record ConcreteEntry(byte[] code) implements BlockEntry {

		@Override
		public byte[] encode(long rip) {
			return code;
		}
	}

	record FuzzEntry(ResourcePartition partition, Opcode opcode, Instruction instruction,
					 Collection<DeferredMutation> mutations) implements BlockEntry {
		public byte[] encode(long rip) throws Block.UnencodeableException {
			class inner {
				private static final ThreadLocal<CodeAssembler> assembler = ThreadLocal.withInitial(() -> new CodeAssembler(64));
			}
			var instructionAssembler = inner.assembler.get();

			try {
				byte[] result = new byte[15];
				var buf = ByteBuffer.wrap(result);
				instructionAssembler.reset();
				instructionAssembler.addInstruction(instruction);
				instructionAssembler.assemble(buf::put, rip);
				var trimmed = new byte[buf.position()];
				System.arraycopy(result, 0, trimmed, 0, trimmed.length);

				for (DeferredMutation mutation : mutations) {
					trimmed = mutation.perform(trimmed);
				}

				return trimmed;
			} catch (EncoderException e) {
				throw new Block.UnencodeableException(e, opcode(), instruction());
			}
		}
	}
}
