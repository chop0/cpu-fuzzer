package ax.xz.fuzz.mutate;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.instruction.x86.X86InstructionBuilder;
import ax.xz.fuzz.instruction.x86.X86Opcode;
import ax.xz.fuzz.instruction.x86.x86RegisterBanks;

import java.util.random.RandomGenerator;

import static ax.xz.fuzz.mutate.Encoding.isLegacyPrefix;

public class RexAdder implements Mutator<X86Opcode, X86InstructionBuilder> {
	@Override
	public boolean appliesTo(ResourcePartition rp, X86Opcode code, X86InstructionBuilder instruction) {
		return Encoding.touches(instruction.instruction(), x86RegisterBanks.GPQ) || Encoding.usesVexEvex(instruction.instruction()); // only add if there's already a rex that'll take precedence
	}

	@Override
	public boolean comesFrom(ResourcePartition rp, X86Opcode code, X86InstructionBuilder instruction, DeferredMutation outcome) {
		return outcome instanceof RexMutation;
	}

	@Override
	public DeferredMutation select(RandomGenerator rng, ResourcePartition rp, X86InstructionBuilder instruction) {
		return new RexMutation((byte) ((0x40 | (byte) rng.nextInt(0x10))));
	}

	private record RexMutation(byte rex) implements DeferredMutation {
		@Override
		public byte[] perform(byte[] insnEncoded) {
			// find end of prefixes
			int targetPosition = 0;
			for (byte b : insnEncoded) {
				if (isLegacyPrefix(b))
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
