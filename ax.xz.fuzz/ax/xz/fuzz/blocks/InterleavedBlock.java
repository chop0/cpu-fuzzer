package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.mutate.DeferredMutation;
import com.github.icedland.iced.x86.Instruction;

import java.util.Arrays;

public class InterleavedBlock extends BasicBlock {
	private final int[] lhsIndices, rhsIndices;
	private final ResourcePartition[]  partitions;

	public InterleavedBlock(Opcode[] opcodes, ResourcePartition[] partitions, Instruction[] instructions, DeferredMutation[][] mutations, int[] lhsIndices, int[] rhsIndices) {
		super(opcodes, instructions, mutations);
		this.lhsIndices = lhsIndices;
		this.rhsIndices = rhsIndices;

		this.partitions = partitions;
	}

	public int[] lhsIndices() {
		return lhsIndices;
	}

	public int[] rhsIndices() {
		return rhsIndices;
	}

	public ResourcePartition partitionOf(int index) {
		return partitions[index];
	}

	@Override
	public InterleavedBlock without(int instructionIndex) {
		boolean isInRhs = Arrays.stream(rhsIndices).anyMatch(n -> n == instructionIndex);

		var lhsIndices = new int[isInRhs ? this.lhsIndices.length : this.lhsIndices.length - 1];
		var rhsIndices = new int[isInRhs ? this.rhsIndices.length - 1 : this.rhsIndices.length];

		var opcodes = new Opcode[opcodes().length - 1];
		var partitions = new ResourcePartition[opcodes().length - 1];
		var mutations = new DeferredMutation[opcodes().length - 1][];
		var instructions = new Instruction[instructions().length - 1];

		int overallIndex = 0, lhsIndex = 0, rhsIndex = 0;

		for (int i = 0; i < this.lhsIndices.length + this.rhsIndices.length; i++) {
			if (i == instructionIndex) {
				continue;
			}

			opcodes[overallIndex] = opcodes()[i];
			partitions[overallIndex] = this.partitions[i];
			mutations[overallIndex] = this.mutations()[i];
			instructions[overallIndex] = instructions()[i];

			if (instructions()[i] == null) {
				throw new NullPointerException("Instruction is null");
			}

			overallIndex++;

			int finalI = i;
			if ((Arrays.stream(lhsIndices).anyMatch(n -> n == finalI) && lhsIndex < lhsIndices.length) || rhsIndex >= rhsIndices.length) {
				lhsIndices[lhsIndex++] = i;
			} else {
				rhsIndices[rhsIndex++] = i;
			}
		}

		if (overallIndex != opcodes.length) {
			throw new IllegalStateException("Index mismatch");
		}

		return new InterleavedBlock(opcodes, partitions, instructions, mutations, lhsIndices, rhsIndices);
	}
}
