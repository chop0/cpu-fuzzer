package ax.xz.fuzz.x86.mutate;

import ax.xz.fuzz.mutate.DeferredMutation;
import ax.xz.fuzz.mutate.Mutator;
import ax.xz.fuzz.x86.operand.Operand;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.x86.arch.X86InstructionBuilder;
import ax.xz.fuzz.x86.arch.X86Opcode;
import com.github.icedland.iced.x86.Instruction;

import java.util.*;
import java.util.random.RandomGenerator;

import static ax.xz.fuzz.x86.arch.x86RegisterDescriptor.*;

public class PrefixAdder implements Mutator<X86Opcode, X86InstructionBuilder> {
	private static final byte[] PREFIXES = {
			(byte) 0xF0, (byte) 0xF2, (byte) 0xF3,
			(byte) 0x2E, (byte) 0x36, (byte) 0x3E, (byte) 0x26, (byte) 0x64, (byte) 0x65,
			(byte) 0x66, (byte) 0x67
	};
	private static final Map<Byte, Integer> prefixIndices;
	static {
		var indices = new HashMap<Byte, Integer>();
		for (int i = 0; i < PREFIXES.length; i++) {
			indices.put(PREFIXES[i], i);
		}
		prefixIndices = Collections.unmodifiableMap(indices);
	}

	@Override
	public boolean appliesTo(ResourcePartition rp, X86Opcode opcode, X86InstructionBuilder instruction) {
		for (Operand op : opcode.operands()) {
			if (op.counted()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public DeferredMutation select(RandomGenerator rng, ResourcePartition rp, X86InstructionBuilder instruction) {
		int addedCount = rng.nextInt(4);
		var prefixes = new ArrayList<AddedPrefix>(addedCount);

		for (int i = 0; i < addedCount; i++) {
			byte addedPrefix;
			do {
				addedPrefix =  PREFIXES[rng.nextInt(PREFIXES.length)];
			} while (!canUsePrefix(instruction.instruction(), rp, addedPrefix));

			prefixes.add(new AddedPrefix(addedPrefix));
		}

		return new PrefixMutation(prefixes);
	}

	private boolean canUsePrefix(Instruction insn, ResourcePartition rp, byte prefix) {
		return switch (prefix) {
			case 0x2e -> rp.allowedRegisters().hasRegister(CS);
			case 0x36 -> rp.allowedRegisters().hasRegister(SS);
			case 0x3e -> rp.allowedRegisters().hasRegister(DS);
			case 0x26 -> rp.allowedRegisters().hasRegister(ES);
			case 0x64 -> rp.allowedRegisters().hasRegister(FS);
			case 0x65 -> rp.allowedRegisters().hasRegister(GS);
			case 0x66, 0x67 -> Encoding.hasImmediate(insn);
			default -> true;
		};
	}

	private record AddedPrefix(byte prefix) {}

	private static record PrefixMutation(List<AddedPrefix> addedPrefixes) implements DeferredMutation {

		@Override
		public byte[] perform(byte[] instruction) {
			byte[] result = new byte[instruction.length + addedPrefixes.size()];
			for (int i = 0; i < addedPrefixes.size(); i++) {
				result[i] = addedPrefixes.get(i).prefix;
			}

			System.arraycopy(instruction, 0, result, addedPrefixes.size(), instruction.length);
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
}
