package ax.xz.fuzz.mutate;

import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.OpKind;
import com.github.icedland.iced.x86.Register;

import java.util.random.RandomGenerator;

import static com.github.icedland.iced.x86.Register.*;
import static java.lang.Math.min;

public class PrefixDuplicator implements Mutator {
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
	public boolean appliesTo(Instruction instruction, ResourcePartition rp) {
		return isRex(instruction) || instruction.getRepePrefix() || instruction.getRepnePrefix() || instruction.getRepPrefix() || instruction.getLockPrefix() || instruction.getSegmentPrefix() != Register.NONE;
	}

	private boolean isRex(Instruction insn) {
		for (int i = 0; i < insn.getOpCount(); i++) {
			if (insn.getOpKind(i) == OpKind.REGISTER)
				if (Register.isGPR64(insn.getOpRegister(i)))
					return true;
		}

		return false;
	}


	@Override
	public DeferredMutation createMutation(Instruction instruction, RandomGenerator rng, ResourcePartition rp) {
		return insnEncoded -> {
			if (instruction.getRepePrefix() && rng.nextBoolean()) {
				insnEncoded = duplicatePrefix(rng, insnEncoded, (byte) 0xF3);
			}

			if (instruction.getRepnePrefix() && rng.nextBoolean()) {
				insnEncoded = duplicatePrefix(rng, insnEncoded, (byte) 0xF2);
			}

			if (instruction.getRepPrefix() && rng.nextBoolean()) {
				insnEncoded = duplicatePrefix(rng, insnEncoded, (byte) 0xF3);
			}

			if (instruction.getLockPrefix() && rng.nextBoolean()) {
				insnEncoded = duplicatePrefix(rng, insnEncoded, (byte) 0xF0);
			}

			if (instruction.getSegmentPrefix() != Register.NONE && rng.nextBoolean()) {
				var prefixValue = switch (instruction.getSegmentPrefix()) {
					case CS -> (byte) 0x2e;
					case SS -> (byte) 0x36;
					case DS -> (byte) 0x3e;
					case ES -> (byte) 0x26;
					case FS -> (byte) 0x64;
					case GS -> (byte) 0x65;
					default -> throw new IllegalStateException("Unexpected value: " + instruction.getSegmentPrefix());
				};
				insnEncoded = duplicatePrefix(rng, insnEncoded, prefixValue);
			}

			// scan for rex prefix and duplicate
			if (isRex(instruction) && rng.nextBoolean()) {
				byte rex = 0;
				for (byte b : insnEncoded) {
					if ((b & 0xF0) == 0x40) {
						rex = b;
						break;
					}
				}

				if (rex != 0)
					insnEncoded = duplicatePrefix(rng, insnEncoded, rex);
			}

			return insnEncoded;
		};
	}

	private byte[] duplicatePrefix(RandomGenerator rng, byte[] insnEncoded, byte prefix) {
		int prefixCount = 0;
		for (prefixCount = 0; prefixCount < insnEncoded.length; prefixCount++) {
			if (!isPrefix(insnEncoded[prefixCount])) {
				break;
			}
		}

		if (prefixCount == 0) {
			return insnEncoded;
		}

		int victimIndex = rng.nextInt(prefixCount);
		byte victim = insnEncoded[victimIndex];

		int duplicationCount = rng.nextInt(4 - min(4, prefixCount));
		var result = new byte[insnEncoded.length + duplicationCount];
		System.arraycopy(insnEncoded, 0, result, 0, victimIndex);
		for (int i = 0; i < duplicationCount; i++) {
			result[victimIndex + i] = victim;
		}
		System.arraycopy(insnEncoded, victimIndex, result, victimIndex + duplicationCount, insnEncoded.length - victimIndex);

		return result;
	}
}
