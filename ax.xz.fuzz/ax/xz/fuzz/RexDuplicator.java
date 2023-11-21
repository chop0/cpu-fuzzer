package ax.xz.fuzz;

import java.util.random.RandomGenerator;

public class RexDuplicator implements PreservingMutation {
	@Override
	public boolean appliesTo(byte[] instruction) {
		return isPrefix(instruction[0]);
	}

	@Override
	public byte[] mutate( byte[] instruction) {
		var result = new byte[instruction.length + 1];
		result[0] = instruction[0];
		System.arraycopy(instruction, 0, result, 1, instruction.length);
		return result;
	}

	private boolean isPrefix(byte b) {
		// rex
		return (b & 0xF0) == 0x40 || b == 0x66 || b == 0x67;
	}
}
