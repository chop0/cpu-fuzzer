package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.InstructionBuilder;
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
	byte[] encode(long rip);

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

			return Arrays.equals(encode(0), that.encode(0));
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

	record FuzzEntry(InstructionBuilder instruction, Collection<DeferredMutation> mutations) implements BlockEntry {
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof BlockEntry that)) return false;

			return Arrays.equals(encode(0), that.encode(0));
		}

		public byte[] encode(long rip) {
			return instruction().encode(rip);
		}
	}
}
