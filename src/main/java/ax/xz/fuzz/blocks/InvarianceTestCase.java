package ax.xz.fuzz.blocks;

import ax.xz.fuzz.runtime.*;
import ax.xz.fuzz.runtime.state.CPUState;

public record InvarianceTestCase(Branch[] branches, ExecutableSequence a, ExecutableSequence b, CPUState initialState) implements TestCase {
	@Override
	public Block[] blocksA() {
		return a.blocks();
	}

	@Override
	public Block[] blocksB() {
		return b.blocks();
	}
}
