package ax.xz.fuzz.mutate;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.Operand;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;

import java.util.Arrays;
import java.util.random.RandomGenerator;

import static ax.xz.fuzz.mutate.Prefixes.isPrefix;

public class VexAdder implements Mutator {

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
		return new VexMutation((byte)rng.nextInt(), (byte)rng.nextInt(), rng.nextBoolean(), rng.nextBoolean());
	}

	private class VexMutation implements DeferredMutation {
		private final byte vex1, vex2;
		private final boolean hasVex2;
		private final boolean enable;

		public VexMutation(byte vex1, byte vex2, boolean hasVex2, boolean enable) {
			this.vex1 = vex1;
			this.vex2 = vex2;
			this.hasVex2 = hasVex2;
			this.enable = enable;
		}

		@Override
		public byte[] perform(byte[] insnEncoded) {
			if (!enable)
				return insnEncoded;

			// find end of prefixes
			int targetPosition = 0;
			for (byte b : insnEncoded) {
				if (isPrefix(b))
					targetPosition++;
				else
					break;
			}

			// nsert at target position
			var newInsnEncoded = new byte[insnEncoded.length + (hasVex2 ? 3 : 2)];
			System.arraycopy(insnEncoded, 0, newInsnEncoded, 0, targetPosition);
			newInsnEncoded[targetPosition] = (byte) (hasVex2 ? 0b11000100 : 0b11000101);
			newInsnEncoded[targetPosition + 1] = vex1;
			if (hasVex2) {
				newInsnEncoded[targetPosition + 2] = vex2;
			}
			System.arraycopy(insnEncoded, targetPosition, newInsnEncoded, targetPosition + (hasVex2 ? 3 : 2), insnEncoded.length - targetPosition);

			return newInsnEncoded;
		}

		@Override
		public String toString() {
			return "vex";
		}
	}
}
