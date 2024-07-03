package ax.xz.fuzz.mutate;

import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import static com.github.icedland.iced.x86.Register.*;
import static java.lang.Math.min;

public class PrefixAdder implements Mutator {
	private static final byte[] PREFIXES = {
			(byte) 0xF0, //(byte) 0xF2, (byte) 0xF3,
			(byte) 0x2E, (byte) 0x36, (byte) 0x3E, (byte) 0x26, (byte) 0x64, (byte) 0x65,
//			(byte) 0x66, (byte) 0x67
	};

	@Override
	public boolean appliesTo(Instruction instruction, ResourcePartition rp) {
		return true;
	}

	@Override
	public DeferredMutation createMutation(Instruction instruction, RandomGenerator rng, ResourcePartition rp) {
		record AddedPrefix(byte prefix) {}

		class PrefixMutation implements DeferredMutation {
			private final List<AddedPrefix> addedPrefixes;

			public PrefixMutation(List<AddedPrefix> addedPrefixes) {
				this.addedPrefixes = addedPrefixes;
			}

			@Override
			public byte[] perform(byte[] instruction) {
				byte[] result = new byte[instruction.length + addedPrefixes.size() + 2];
				for (int i = 0; i < addedPrefixes.size(); i++) {
					result[i] = addedPrefixes.get(i).prefix;
				}

				System.arraycopy(instruction, 0, result, addedPrefixes.size(), instruction.length);
				result[result.length - 2] = (byte) 0x90;
				result[result.length - 1] = (byte) 0x90; // NOP in case the operand extension prefix fucks up the decoding

				return result;
			}

			@Override
			public String toString() {
				var sb = new StringBuilder();
				sb.append("PrefixMutation{");
				for (var prefix : addedPrefixes) {
					sb.append(String.format("0x%02X ", prefix.prefix));
				}
				sb.append("}");
				return sb.toString();
			}
		}

		int addedCount = rng.nextInt(4);
		var prefixes = new ArrayList<AddedPrefix>(addedCount);

		for (int i = 0; i < addedCount; i++) {
			byte addedPrefix;
			do {
				addedPrefix =  PREFIXES[rng.nextInt(PREFIXES.length)];
			} while (!canUsePrefix(rp, addedPrefix));

			prefixes.add(new AddedPrefix(addedPrefix));
		}

		return new PrefixMutation(prefixes);
	}

	private boolean canUsePrefix(ResourcePartition rp, byte prefix) {
		return switch (prefix) {
			case 0x2e -> rp.allowedRegisters().hasRegister(CS);
			case 0x36 -> rp.allowedRegisters().hasRegister(SS);
			case 0x3e -> rp.allowedRegisters().hasRegister(DS);
			case 0x26 -> rp.allowedRegisters().hasRegister(ES);
			case 0x64 -> rp.allowedRegisters().hasRegister(FS);
			case 0x65 -> rp.allowedRegisters().hasRegister(GS);
			default -> true;
		};
	}

	private boolean isPrefix(byte b) {
		for (var prefix : PREFIXES) {
			if (b == prefix)
				return true;
		}

		return false;
	}
}
