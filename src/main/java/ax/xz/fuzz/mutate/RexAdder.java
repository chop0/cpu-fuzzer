package ax.xz.fuzz.mutate;

import ax.xz.fuzz.blocks.randomisers.ReverseRandomGenerator;
import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.Operand;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;

import java.util.random.RandomGenerator;

import static ax.xz.fuzz.mutate.Prefixes.isPrefix;

public class RexAdder implements Mutator {
	@Override
	public boolean appliesTo(ResourcePartition rp, Opcode code, Instruction instruction) {
		for (Operand op : code.operands()) {
			if (op.counted()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean comesFrom(ResourcePartition rp, Opcode code, Instruction instruction, DeferredMutation outcome) {
		return outcome instanceof RexMutation;
	}

	@Override
	public DeferredMutation select(RandomGenerator rng, ResourcePartition rp, Instruction instruction) {
		return new RexMutation((byte) ((0x40 | (byte) rng.nextInt(0x10)) & ~(1)));
	}

	@Override
	public void reverse(ReverseRandomGenerator rng, ResourcePartition rp, Instruction instruction, DeferredMutation outcome) {
		var mutation = (RexMutation) outcome;
		rng.pushInt(mutation.rex & 0b1111);
	}

	private record RexMutation(byte rex) implements DeferredMutation {
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
