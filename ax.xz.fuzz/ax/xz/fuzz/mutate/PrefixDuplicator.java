package ax.xz.fuzz.mutate;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.OpKind;
import com.github.icedland.iced.x86.Register;

import java.util.Map;
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
	public boolean appliesTo(Opcode code, Instruction instruction, ResourcePartition rp) {
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
		return new PrefixDupeMutation(instruction, rng);
	}

	private byte[] duplicatePrefix(int duplicationCount, byte[] insnEncoded, byte prefix) {
		int prefixCount = 0;
		for (byte b : insnEncoded) {
			if (isPrefix(b))
				prefixCount++;
			else
				break;
		}

		int victimIndex = -1;
		for (int i = 0; i < prefixCount; i++) {
			if (insnEncoded[i] == prefix) {
				victimIndex = i;
				break;
			}
		}

		if (victimIndex == -1)
			return insnEncoded;

		byte victim = insnEncoded[victimIndex];

		var result = new byte[insnEncoded.length + duplicationCount];
		System.arraycopy(insnEncoded, 0, result, 0, victimIndex);
		for (int i = 0; i < duplicationCount; i++) {
			result[victimIndex + i] = victim;
		}
		System.arraycopy(insnEncoded, victimIndex, result, victimIndex + duplicationCount, insnEncoded.length - victimIndex);

		return result;
	}

	private class PrefixDupeMutation implements DeferredMutation {
		private final Instruction instruction;

		private final int repeDuplicationCount, repneDuplicationCount, repDuplicationCount, lockDuplicationCount, segmentDuplicationCount, rexDuplicationCount;

		public PrefixDupeMutation(Instruction instruction, RandomGenerator rng) {
			this.instruction = instruction;

			repeDuplicationCount = instruction.getRepePrefix() ? rng.nextInt(2) : 0;
			repneDuplicationCount = instruction.getRepnePrefix() ? rng.nextInt(2) : 0;
			repDuplicationCount = instruction.getRepPrefix() ? rng.nextInt(2) : 0;
			lockDuplicationCount = instruction.getLockPrefix() ? rng.nextInt(2) : 0;
			segmentDuplicationCount = instruction.getSegmentPrefix() != Register.NONE ? rng.nextInt(2) : 0;
			rexDuplicationCount =  rng.nextInt(2);
		}

		@Override
		public byte[] perform(byte[] insnEncoded) {
			insnEncoded = PrefixDuplicator.this.duplicatePrefix(repeDuplicationCount, insnEncoded, (byte) 0xF3);
			insnEncoded = PrefixDuplicator.this.duplicatePrefix(repneDuplicationCount, insnEncoded, (byte) 0xF2);
			insnEncoded = PrefixDuplicator.this.duplicatePrefix(repDuplicationCount, insnEncoded, (byte) 0xF3);
			insnEncoded = PrefixDuplicator.this.duplicatePrefix(lockDuplicationCount, insnEncoded, (byte) 0xF0);
			insnEncoded = PrefixDuplicator.this.duplicatePrefix(segmentDuplicationCount, insnEncoded, switch (instruction.getSegmentPrefix()) {
				case CS -> (byte) 0x2e;
				case SS -> (byte) 0x36;
				case DS -> (byte) 0x3e;
				case ES -> (byte) 0x26;
				case FS -> (byte) 0x64;
				case GS -> (byte) 0x65;
				default -> (byte)0;
			});

			if (rexDuplicationCount != 0) {
				byte rex = 0;
				for (byte b : insnEncoded) {
					if ((b & 0xF0) == 0x40) {
						rex = b;
						break;
					}
				}

				if (rex != 0)
					insnEncoded = PrefixDuplicator.this.duplicatePrefix(rexDuplicationCount, insnEncoded, rex);
			}

			return insnEncoded;
		}

		@Override
		public String toString() {
			var sb = new StringBuilder();
			if (repeDuplicationCount != 0)
				sb.append("repe[").append(repeDuplicationCount).append("] ");
			if (repneDuplicationCount != 0)
				sb.append("repne[").append(repneDuplicationCount).append("] ");
			if (repDuplicationCount != 0)
				sb.append("rep[").append(repDuplicationCount).append("] ");
			if (lockDuplicationCount != 0)
				sb.append("lock[").append(lockDuplicationCount).append("] ");
			if (segmentDuplicationCount != 0)
				sb.append("segment[").append(segmentDuplicationCount).append("] ");
			if (rexDuplicationCount != 0)
				sb.append("rex[").append(rexDuplicationCount).append("] ");

			return sb.toString();
		}
	}
}
