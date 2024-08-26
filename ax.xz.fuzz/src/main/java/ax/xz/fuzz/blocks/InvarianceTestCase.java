package ax.xz.fuzz.blocks;

import ax.xz.fuzz.arch.BlockEdge;
import ax.xz.fuzz.runtime.*;
import ax.xz.fuzz.arch.CPUState;

public record InvarianceTestCase(BlockEdge[] blockEdges, ExecutableSequence a, ExecutableSequence b, CPUState initialState) implements TestCase {
	@Override
	public Block[] blocksA() {
		return a.blocks();
	}

	@Override
	public Block[] blocksB() {
		return b.blocks();
	}
}
