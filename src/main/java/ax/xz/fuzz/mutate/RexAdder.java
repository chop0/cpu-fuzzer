package ax.xz.fuzz.mutate;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.OpKind;

import java.util.random.RandomGenerator;

import static ax.xz.fuzz.mutate.Encoding.isPrefix;

public class RexAdder implements Mutator {
	@Override
	public boolean appliesTo(ResourcePartition rp, Opcode code, Instruction instruction) {
		return false;
	}

	@Override
	public boolean comesFrom(ResourcePartition rp, Opcode code, Instruction instruction, DeferredMutation outcome) {
		return outcome instanceof RexMutation;
	}

	@Override
	public DeferredMutation select(RandomGenerator rng, ResourcePartition rp, Instruction instruction) {
		byte bitsDisableMask = 0b0101;
		if (Encoding.hasImmediate(instruction))
			bitsDisableMask |= 0b1000; // can fuck up length decoding
		if (Encoding.hasSIBIndex(instruction) || instruction.hasOpKind(OpKind.MEMORY))
			bitsDisableMask |= 0b0010;
		if (Encoding.hasSIBBase(instruction))
			bitsDisableMask |= 0b0001;

		return new RexMutation((byte) ((0x40 | (byte) rng.nextInt(0x10)) & ~(bitsDisableMask)));
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
