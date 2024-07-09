package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.mutate.DeferredMutation;
import ax.xz.fuzz.runtime.Trampoline;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.enc.Encoder;
import com.github.icedland.iced.x86.enc.EncoderException;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.random.RandomGenerator;

import static ax.xz.fuzz.runtime.TestCase.TEST_CASE_FINISH;

public interface Block {
	default int[] encode(Trampoline trampoline, MemorySegment code) throws UnencodeableException {
		var codeBuf = code.asByteBuffer();
		var encoder = new Encoder(64, codeBuf::put);

		int[] locations = new int[size() * 15];

		int position = 0;
		int rip = 0;
		for (var item : items()) {
			try {
				var insn = item.instruction();
				int insnLen = encoder.encode(insn, code.address() + codeBuf.position());
				for (int k = 0; k < insnLen; k++) {
					locations[rip++] = position;
				}
				position++;
			} catch (EncoderException e) {
				throw new UnencodeableException(e, item.opcode(), item.instruction());
			}
		}

		var JUMP_TO_FINISH = new byte[6 + 8];
		ByteBuffer.wrap(JUMP_TO_FINISH)
				.order(ByteOrder.nativeOrder())
				.put(new byte[]{(byte) 0xFF, 0x25, 0x00, 0x00, 0x00, 0x00})
				.putLong(trampoline.relocate(TEST_CASE_FINISH).address());

		codeBuf.put(JUMP_TO_FINISH);

		return Arrays.copyOf(locations, rip);
	}

	int size();
	SequencedCollection<BlockEntry> items();
	default Block without(int... instructionIndex) {
		return new SkipBlock(this, Arrays.stream(instructionIndex).boxed().toList());
	}

	public static InterleavedBlock randomlyInterleaved(RandomGenerator rng, Block lhs, Block rhs) {
		var picks = new BitSet(lhs.size() + rhs.size());

		{
			int lhsIndex = 0, rhsIndex = 0;
			for (int i = 0; i < (lhs.size() + rhs.size()); i++) {
				if ((rng.nextBoolean() && lhsIndex < lhs.size()) || rhsIndex >= rhs.size()) {
					picks.set(i, true);
					lhsIndex++;
				} else {
					rhsIndex++;
					picks.set(i, false);
				}
			}
		}

		return new InterleavedBlock(lhs, rhs, picks);
	}


	public static class UnencodeableException extends Exception {
		public final Opcode opcode;
		public final Instruction instruction;

		public UnencodeableException(Throwable cause, Opcode opcode, Instruction instruction) {
			super(cause);
			this.opcode = opcode;
			this.instruction = instruction;
		}
	}

	public record BlockEntry(ResourcePartition partition, Opcode opcode, Instruction instruction, Collection<DeferredMutation> mutations) {}
}
