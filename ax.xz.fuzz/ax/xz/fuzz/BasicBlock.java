package ax.xz.fuzz;

import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.enc.Encoder;
import com.github.icedland.iced.x86.enc.EncoderException;

import java.lang.foreign.MemorySegment;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Objects;
import java.util.random.RandomGenerator;


public class BasicBlock {
	private static final byte[] JUMP_TO_FINISH;

	static {
		JUMP_TO_FINISH = new byte[6 + 8];
		ByteBuffer.wrap(JUMP_TO_FINISH)
				.order(ByteOrder.nativeOrder())
				.put(new byte[]{(byte) 0xFF, 0x25, 0x00, 0x00, 0x00, 0x00})
				.putLong(TestCase.TEST_CASE_FINISH.address());
	}

	private final Opcode[] opcodes;
	private final Instruction[] instructions;

	public BasicBlock(Opcode[] opcodes, Instruction[] instructions) {
		if (opcodes.length != instructions.length)
			throw new IllegalArgumentException("opcodes.length != instructions.length");
		this.opcodes = opcodes;
		this.instructions = instructions;
	}

	static InterleavedBlock randomlyInterleaved(RandomGenerator rng, BasicBlock lhs, BasicBlock rhs) {
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

		var bb = new BasicBlock(pick(picks, lhs.opcodes(), rhs.opcodes()), pick(picks, lhs.instructions(), rhs.instructions()));

		int lhsIndex = 0, rhsIndex = 0;

		var lhsIndices = new int[lhs.size()];
		var rhsIndices = new int[rhs.size()];

		for (int i = 0; i < bb.size(); i++) {
			if (picks.get(i)) {
				lhsIndices[lhsIndex++] = i;
			} else {
				rhsIndices[rhsIndex++] = i;
			}
		}

		return new InterleavedBlock(bb.opcodes(), bb.instructions(), lhsIndices, rhsIndices);
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

		codeBuf.put(JUMP_TO_FINISH);

		return Arrays.copyOf(locations, rip);
	}

	int size() {
		return instructions.length;
	}

	@Override
	public String toString() {
		return Arrays.stream(instructions)
				.map(n -> {
					var res = "";
					if (n.getRepPrefix())
						res += "rep ";
					if (n.getRepnePrefix())
						res += "repne ";
					if (n.getLockPrefix())
						res += "lock ";
					if (n.getRepePrefix())
						res += "repe ";
					res += n.toString();
					return res;
				})
				.reduce((a, b) -> a + "\n" + b)
				.orElse("");
	}

	public Opcode[] opcodes() {
		return opcodes;
	}

	public Instruction[] instructions() {
		return instructions;
	}

	public BasicBlock without(int instructionIndex) {
		var newOpcodes = new Opcode[opcodes.length - 1];
		var newInstructions = new Instruction[instructions.length - 1];

		int newOpcodeIndex = 0;
		for (int i = 0; i < opcodes.length; i++) {
			if (i == instructionIndex) continue;
			newOpcodes[newOpcodeIndex] = opcodes[i];
			newInstructions[newOpcodeIndex] = instructions[i];
			newOpcodeIndex++;
		}

		return new BasicBlock(newOpcodes, newInstructions);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (BasicBlock) obj;
		return Arrays.equals(this.opcodes, that.opcodes) &&
			   Arrays.equals(this.instructions, that.instructions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(opcodes), Arrays.hashCode(instructions));
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
