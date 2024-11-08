package ax.xz.fuzz.x86.mutate;

import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.x86.arch.x86RegisterDescriptor;
import com.github.icedland.iced.x86.EncodingKind;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.OpKind;
import com.github.icedland.iced.x86.Register;

public class Encoding {
	private static final byte[] PREFIX_GROUP_1 = {(byte) 0xF0, (byte) 0xF2, (byte) 0xF3};
	private static final byte[] PREFIX_GROUP_2 = {(byte) 0x2E, (byte) 0x36, (byte) 0x3E, (byte) 0x26, (byte) 0x64, (byte) 0x65};
	private static final byte[] PREFIX_GROUP_3 = {(byte) 0x66, (byte) 0x67};

	private static final byte[] PREFIXES;
	static {
		PREFIXES = new byte[PREFIX_GROUP_1.length + PREFIX_GROUP_2.length + PREFIX_GROUP_3.length];
		System.arraycopy(PREFIX_GROUP_1, 0, PREFIXES, 0, PREFIX_GROUP_1.length);
		System.arraycopy(PREFIX_GROUP_2, 0, PREFIXES, PREFIX_GROUP_1.length, PREFIX_GROUP_2.length);
		System.arraycopy(PREFIX_GROUP_3, 0, PREFIXES, PREFIX_GROUP_1.length + PREFIX_GROUP_2.length, PREFIX_GROUP_3.length);
	}


	static boolean isLegacyPrefix(byte b) {
		for (var prefix : PREFIXES) {
			if (b == prefix)
				return true;
		}

		return false;
	}

	static boolean hasImmediate(Instruction instruction) {
		for (int i = 0; i < instruction.getOpCount(); i++) {
			int opkind = instruction.getOpKind(i);
			if (opkind >= OpKind.IMMEDIATE8 && opkind <= OpKind.IMMEDIATE32TO64) {
				return true;
			}
		}

		return false;
	}

	static boolean touches(Instruction instruction, RegisterSet set) {
		for (int i = 0; i < instruction.getOpCount(); i++) {
			int opkind = instruction.getOpKind(i);
			if (opkind == OpKind.REGISTER) {
				int register = instruction.getOpRegister(i);
				if (set.hasRegister(x86RegisterDescriptor.byIcedId(register)))
					return true;
			}
		}

		return false;
	}

	static boolean usesVexEvex(Instruction instruction) {
		return instruction.getOpCode().getEncoding() == EncodingKind.VEX || instruction.getOpCode().getEncoding() == EncodingKind.EVEX;
	}

	static boolean hasSIBIndex(Instruction instruction) {
		return instruction.getMemoryIndex() != Register.NONE;
	}

	static boolean hasSIBBase(Instruction instruction) {
		return instruction.getMemoryBase() != Register.NONE;
	}
}
