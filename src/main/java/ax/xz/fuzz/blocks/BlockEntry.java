package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.mutate.DeferredMutation;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.asm.CodeAssembler;
import com.github.icedland.iced.x86.enc.EncoderException;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;

public sealed interface BlockEntry permits BlockEntry.ConcreteEntry, BlockEntry.FuzzEntry, InterleavedBlock.InterleavedEntry {
	byte[] encode(long rip) throws Block.UnencodeableException;

	static BlockEntry nop1() {
		return new ConcreteEntry(new byte[]{(byte) 0x90});
	}

	static BlockEntry nop3() {
		return new ConcreteEntry(new byte[]{(byte) 0x0f, (byte) 0x1f, (byte) 0x00});
	}

	record ConcreteEntry(byte[] code) implements BlockEntry {

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof BlockEntry that)) return false;

			try {
				return Arrays.equals(encode(0), that.encode(0));
			} catch (Block.UnencodeableException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public byte[] encode(long rip) {
			return code;
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(code);
		}
	}

	record FuzzEntry(ResourcePartition partition, Opcode opcode, Instruction instruction,
			 Collection<DeferredMutation> mutations) implements BlockEntry {
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof BlockEntry that)) return false;

			try {
				return Arrays.equals(encode(0), that.encode(0));
			} catch (Block.UnencodeableException e) {
				throw new RuntimeException(e);
			}
		}

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
