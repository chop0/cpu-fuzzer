package ax.xz.fuzz.mutate;

public class Prefixes {
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


	static boolean isPrefix(byte b) {
		// rex
		if ((b & 0xF0) == 0x40)
			return true;

		for (var prefix : PREFIXES) {
			if (b == prefix)
				return true;
		}

		return false;
	}
}
