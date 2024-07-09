package ax.xz.fuzz.mutate;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.Operand;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;

import java.util.Arrays;
import java.util.random.RandomGenerator;

public class RexAdder implements Mutator {
	private static final byte[] PREFIX_GROUP_1 = {(byte) 0xF0, (byte) 0xF2, (byte) 0xF3};
	private static final byte[] PREFIX_GROUP_2 = {(byte) 0x2E, (byte) 0x36, (byte) 0x3E, (byte) 0x26, (byte) 0x64, (byte) 0x65};
	private static final byte[] PREFIX_GROUP_3 = {(byte) 0x66, (byte) 0x67};

	private static final byte[][] PREFIX_GROUPS = {PREFIX_GROUP_1, PREFIX_GROUP_2, PREFIX_GROUP_3};


	private boolean isPrefix(byte b) {
		// rex
		if ((b & 0xF0) == 0x40)
			return true;

		for (var group : PREFIX_GROUPS) {
			for (var prefix : group) {
				if (b == prefix)
					return true;
			}
		}

		return false;
	}

	@Override
	public boolean appliesTo(Opcode code, Instruction instruction, ResourcePartition rp) {
		return Arrays.stream(code.operands()).noneMatch(op -> op instanceof Operand.Counted);
	}

	@Override
	public DeferredMutation createMutation(Instruction instruction, RandomGenerator rng, ResourcePartition rp) {
		return new RexMutation((byte) ((0x40 | (byte) rng.nextInt(0x10)) & ~(1)));
	}

	private class RexMutation implements DeferredMutation {
		private final byte rex;

		public RexMutation(byte rex) {
			this.rex = rex;
		}

		@Override
		public byte[] perform(byte[] insnEncoded) {
			// find end of prefixes
			int targetPosition = 0;
			for (byte b : insnEncoded) {
				if (isPrefix(b))
					targetPosition++;
				else
					break;
			}

			// nsert at target position
			var newInsnEncoded = new byte[insnEncoded.length + 1];
			System.arraycopy(insnEncoded, 0, newInsnEncoded, 0, targetPosition);
			newInsnEncoded[targetPosition] = rex;
			System.arraycopy(insnEncoded, targetPosition, newInsnEncoded, targetPosition + 1, insnEncoded.length - targetPosition);

			return newInsnEncoded;
		}

		@Override
		public String toString() {
			return "rex";
		}
	}
}
