package ax.xz.fuzz;

import com.github.icedland.iced.x86.Instruction;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Objects;
import java.util.random.RandomGenerator;

public class BasicBlock {
	private final Opcode[] opcodes;
	private final Instruction[] instructions;

	public BasicBlock(Opcode[] opcodes, Instruction[] instructions) {
		if (opcodes.length != instructions.length)
			throw new IllegalArgumentException("opcodes.length != instructions.length");
		this.opcodes = opcodes;
		this.instructions = instructions;
	}

	static BasicBlock randomlyInterleaved(RandomGenerator rng, BasicBlock lhs, BasicBlock rhs) {
		var picks = new BitSet(lhs.size() + rhs.size());

		for (int i = 0; i < (lhs.size() + rhs.size()); i++)
			picks.set(i, rng.nextBoolean());

		return new BasicBlock(pick(picks, lhs.opcodes(), rhs.opcodes()), pick(picks, lhs.instructions(), rhs.instructions()));
	}

	int size() {
		return instructions.length;
	}

	@Override
	public String toString() {
		return Arrays.stream(instructions)
				.map(Instruction::toString)
				.reduce((a, b) -> a + "\n" + b)
				.orElse("");
	}

	public Opcode[] opcodes() {
		return opcodes;
	}

	public Instruction[] instructions() {
		return instructions;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (BasicBlock) obj;
		return Arrays.equals(this.opcodes, that.opcodes) &&
			   Arrays.equals(this.instructions, that.instructions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(opcodes), Arrays.hashCode(instructions));
	}

	private static <T> T[] pick(BitSet picks, T[] lhs, T[] rhs) {
		var result = (T[]) Array.newInstance(lhs.getClass().getComponentType(), lhs.length + rhs.length);

		int lhsIndex = 0, rhsIndex = 0;
		for (int i = 0; i < result.length; i++) {
			if (picks.get(i)) result[i] = lhs[lhsIndex++];
			else result[i] = rhs[rhsIndex++];

			if (lhsIndex == lhs.length) {
				System.arraycopy(rhs, rhsIndex, result, i + 1, rhs.length - rhsIndex);
				break;
			} else if (rhsIndex == rhs.length) {
				System.arraycopy(lhs, lhsIndex, result, i + 1, lhs.length - lhsIndex);
				break;
			}
		}

		return result;
	}
}
