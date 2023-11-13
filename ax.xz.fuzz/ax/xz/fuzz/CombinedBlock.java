package ax.xz.fuzz;

import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.enc.Encoder;
import com.github.icedland.iced.x86.enc.EncoderException;

import java.lang.foreign.MemorySegment;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.BitSet;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

public record CombinedBlock(BasicBlock lhs, BasicBlock rhs, BitSet picks) {
	static CombinedBlock randomlyInterleaved(RandomGenerator rng, BasicBlock lhs, BasicBlock rhs) {
		var picks = new BitSet(lhs.size() + rhs.size());

		for (int i = 0; i < (lhs.size() + rhs.size()); i++)
			picks.set(i, rng.nextBoolean());

		return new CombinedBlock(lhs, rhs, picks);
	}

	public Instruction[] instructions() {
		return pick(picks, lhs.instructions(), rhs.instructions());
	}

	public Opcode[] opcodes() {
		return pick(picks, lhs.opcodes(), rhs.opcodes());
	}

	private static <T> T[] pick(BitSet picks, T[] lhs, T[] rhs) {
		var result = (T[]) Array.newInstance(lhs.getClass().getComponentType(), lhs.length + rhs.length);

		int lhsIndex = 0, rhsIndex = 0;
		for (int i = 0; i < result.length; i++) {
			if (picks.get(i)) result[i] = lhs[lhsIndex++];
			else result[i] = rhs[rhsIndex++];

			if (lhsIndex == lhs.length) {
				System.arraycopy(rhs, rhsIndex, result, i + 1, rhs.length - rhsIndex);
				break;
			} else if (rhsIndex == rhs.length) {
				System.arraycopy(lhs, lhsIndex, result, i + 1, lhs.length - lhsIndex);
				break;
			}
		}

		return result;
	}

	@Override
	public String toString() {
		return Arrays.stream(instructions())
				.map(Instruction::toString)
				.reduce((a, b) -> a + "\n" + b)
				.orElse("");
	}

	public int[] encode(MemorySegment code) throws UnencodeableException {
		return encode(code, opcodes(), instructions());
	}

	public static int[] encode(MemorySegment code, Opcode[] opcodes, Instruction[] instructions) throws UnencodeableException {
		int rip = 0;

		var codeBuf = code.asByteBuffer();
		var encoder = new Encoder(64, codeBuf::put);

		int[] locations = new int[instructions.length * 15];

		for (int j = 0; j < instructions.length; j++) {
			try {
				var insn = instructions[j];
				int insnLen = encoder.encode(insn, code.address() + codeBuf.position());
				for (int k = 0; k < insnLen; k++) {
					locations[rip++] = j;
				}
			} catch (EncoderException e) {
				throw new UnencodeableException(e, opcodes[j], instructions[j]);
			}
		}

		return Arrays.copyOf(locations, rip );
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
