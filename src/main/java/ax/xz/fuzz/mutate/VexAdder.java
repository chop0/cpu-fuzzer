package ax.xz.fuzz.mutate;

import ax.xz.fuzz.instruction.InstructionBuilder;
import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.Operand;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.instruction.x86.X86InstructionBuilder;
import ax.xz.fuzz.instruction.x86.X86Opcode;
import com.github.icedland.iced.x86.Instruction;

import java.util.random.RandomGenerator;

import static ax.xz.fuzz.mutate.Encoding.isLegacyPrefix;

public class VexAdder implements Mutator<X86Opcode, X86InstructionBuilder> {

	@Override
	public boolean appliesTo(ResourcePartition rp, X86Opcode code, X86InstructionBuilder instruction) {
		for (Operand op : code.operands()) {
			if (op.counted()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean comesFrom(ResourcePartition rp, X86Opcode code, X86InstructionBuilder instruction, DeferredMutation outcome) {
		return outcome instanceof VexMutation;
	}

	@Override
	public DeferredMutation select(RandomGenerator rng, ResourcePartition rp, X86InstructionBuilder instruction) {
		return new VexMutation((byte) rng.nextInt(), (byte) rng.nextInt(), rng.nextBoolean(), rng.nextBoolean());
	}

	private static record VexMutation(byte vex1, byte vex2, boolean hasVex2, boolean enable) implements DeferredMutation {

		@Override
		public byte[] perform(byte[] insnEncoded) {
			if (!enable)
				return insnEncoded;

			// find end of prefixes
			int targetPosition = 0;
			for (byte b : insnEncoded) {
				if (isLegacyPrefix(b))
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
