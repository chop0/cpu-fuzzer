package ax.xz.fuzz.mutate;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.OpKind;
import com.github.icedland.iced.x86.Register;

import java.util.random.RandomGenerator;

import static ax.xz.fuzz.mutate.Encoding.isPrefix;
import static com.github.icedland.iced.x86.Register.*;

public class PrefixDuplicator implements Mutator {


	@Override
	public boolean appliesTo(ResourcePartition rp, Opcode code, Instruction instruction) {
		return true;
	}

	@Override
	public boolean comesFrom(ResourcePartition rp, Opcode code, Instruction instruction, DeferredMutation outcome) {
		return outcome instanceof PrefixDupeMutation;
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
	public DeferredMutation select(RandomGenerator rng, ResourcePartition rp, Instruction instruction) {
		return new PrefixDupeMutation(instruction,
			instruction.getRepePrefix() ? rng.nextInt(2) : 0,
			instruction.getRepnePrefix() ? rng.nextInt(2) : 0,
			instruction.getRepPrefix() ? rng.nextInt(2) : 0,
			instruction.getLockPrefix() ? rng.nextInt(2) : 0,
			instruction.getSegmentPrefix() != Register.NONE ? rng.nextInt(2) : 0,
			rng.nextInt(2));
	}

	private static byte[] duplicatePrefix(int duplicationCount, byte[] insnEncoded, byte prefix) {
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

	private record PrefixDupeMutation(Instruction instruction, int repeDuplicationCount, int repneDuplicationCount, int repDuplicationCount, int lockDuplicationCount, int segmentDuplicationCount, int rexDuplicationCount)  implements DeferredMutation {
		@Override
		public byte[] perform(byte[] insnEncoded) {
			insnEncoded = duplicatePrefix(repeDuplicationCount, insnEncoded, (byte) 0xF3);
			insnEncoded = duplicatePrefix(repneDuplicationCount, insnEncoded, (byte) 0xF2);
			insnEncoded = duplicatePrefix(repDuplicationCount, insnEncoded, (byte) 0xF3);
			insnEncoded = duplicatePrefix(lockDuplicationCount, insnEncoded, (byte) 0xF0);
			insnEncoded = duplicatePrefix(segmentDuplicationCount, insnEncoded, switch (instruction.getSegmentPrefix()) {
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
					insnEncoded = duplicatePrefix(rexDuplicationCount, insnEncoded, rex);
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
