package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.Block;
import ax.xz.fuzz.blocks.InvarianceTestCase;
import ax.xz.fuzz.runtime.state.CPUState;

public sealed interface TestCase permits InvarianceTestCase, RecordedTestCase {
	CPUState initialState();
	Block[] blocksA();
	Block[] blocksB();
	Branch[] branches();
}
