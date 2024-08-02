package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.Opcode;
import com.github.icedland.iced.x86.Instruction;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.SequencedCollection;

import static ax.xz.fuzz.tester.slave_h.trampoline_return_address;

public interface Block {
	default int[] encode(MemorySegment code) throws UnencodeableException {
		var codeBuf = code.asByteBuffer();

		int[] locations = new int[size() * 15];

		int position = 0;
		int rip = 0;
		for (var item : items()) {
			var insnLen = item.encode(code.address() + codeBuf.position()).length;
			for (int k = 0; k < insnLen; k++) {
				locations[rip++] = position;
			}
			position++;
		}

		var JUMP_TO_FINISH = new byte[6 + 8];
		ByteBuffer.wrap(JUMP_TO_FINISH)
			.order(ByteOrder.nativeOrder())
			.put(new byte[]{(byte) 0xFF, 0x25, 0x00, 0x00, 0x00, 0x00})
			.putLong(trampoline_return_address().address());

		codeBuf.put(JUMP_TO_FINISH);

		return Arrays.copyOf(locations, rip);
	}

	int size();

	SequencedCollection<BlockEntry> items();

	default Block without(int... instructionIndex) {
		return new SkipBlock(this, Arrays.stream(instructionIndex).boxed().toList());
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

}
