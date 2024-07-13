package ax.xz.fuzz.mutate;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.Operand;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;

import java.util.Arrays;
import java.util.random.RandomGenerator;

import static ax.xz.fuzz.mutate.Prefixes.isPrefix;

public class RexAdder implements Mutator {
	@Override
	public boolean appliesTo(Opcode code, Instruction instruction, ResourcePartition rp) {
		for (Operand op : code.operands()) {
			if (op instanceof Operand.Counted) {
				return false;
			}
		}
		return true;
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
