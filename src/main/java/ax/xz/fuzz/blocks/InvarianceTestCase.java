package ax.xz.fuzz.blocks;

import ax.xz.fuzz.runtime.*;
import ax.xz.fuzz.runtime.state.CPUState;

public record InvarianceTestCase(BlockPair[] pairs, Branch[] branches, ExecutableSequence a, ExecutableSequence b, CPUState initialState) {
}
