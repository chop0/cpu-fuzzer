package ax.xz.fuzz;

import com.github.icedland.iced.x86.Instruction;

import java.util.Arrays;

public class InterleavedBlock extends BasicBlock {
	private final int[] lhsIndices, rhsIndices;

	public InterleavedBlock(Opcode[] opcodes, Instruction[] instructions, int[] lhsIndices, int[] rhsIndices) {
		super(opcodes, instructions);
		this.lhsIndices = lhsIndices;
		this.rhsIndices = rhsIndices;
	}

	public int[] lhsIndices() {
		return lhsIndices;
	}

	public int[] rhsIndices() {
		return rhsIndices;
	}

	@Override
	public InterleavedBlock without(int instructionIndex) {
		var lhsIndices = new int[this.lhsIndices.length - 1];
		var rhsIndices = new int[this.rhsIndices.length - 1];

		var opcodes = new Opcode[opcodes().length - 1];
		var instructions = new Instruction[instructions().length - 1];

		int overallIndex = 0, lhsIndex = 0, rhsIndex = 0;

		for (int i = 0; i < lhsIndices.length + rhsIndices.length; i++) {
			if (i == instructionIndex) {
				continue;
			}

			opcodes[overallIndex] = opcodes()[i];
			instructions[overallIndex] = instructions()[i];
			overallIndex++;

			int finalI = i;
			if ((Arrays.stream(lhsIndices).anyMatch(n -> n == finalI) && lhsIndex < lhsIndices.length) || rhsIndex >= rhsIndices.length) {
				lhsIndices[lhsIndex++] = i;
			} else {
				rhsIndices[rhsIndex++] = i;
			}
		}

		return new InterleavedBlock(opcodes, instructions, lhsIndices, rhsIndices);
	}
}
