package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.Block;
import ax.xz.fuzz.blocks.InterleavedBlock;
import ax.xz.fuzz.blocks.InvarianceTestCase;
import ax.xz.fuzz.runtime.state.CPUState;

import java.util.ArrayList;
import java.util.function.BiFunction;

import static ax.xz.fuzz.runtime.ExecutionResult.interestingMismatch;

public class Minimiser {
	private final SequenceExecutor executor;

	public Minimiser(SequenceExecutor executor) {
		this.executor = executor;
	}

	public InvarianceTestCase minimise(InvarianceTestCase tc) {
		var test1 = tc.a().blocks();
		var test2 = tc.b().blocks();

		var branches = tc.branches();
		var startState = tc.initialState();

		for (; ; ) {
			boolean changed = false;

			var simplifiedState = simplifyInitialState(startState, new ExecutableSequence(test1, branches), new ExecutableSequence(test2, branches));
			if (!simplifiedState.equals(startState)) {
				startState = simplifiedState;
				System.err.println("Simplified initial state");
				continue;
			}

			for (int block = 0; block < test1.length; block++) {
				if (!(test1[block] instanceof InterleavedBlock a && test2[block] instanceof InterleavedBlock b)) {
					System.out.println("Skipping non-interleaved block");
					continue;
				}

				var redundantLeft = findRedundantInstructions(a.lhs().size(), startState, test1, test2, branches, block, InterleavedBlock::leftInterleavedIndex);

				if (!redundantLeft.isEmpty()) {
					test1[block] = a.without(redundantLeft.stream().mapToInt(a::leftInterleavedIndex).toArray());
					test2[block] = b.without(redundantLeft.stream().mapToInt(b::leftInterleavedIndex).toArray());

					changed = true;
					System.err.printf("Removed %d redundant instructions from block %d%n", redundantLeft.size(), block);
					break;
				}

				var redundantRight = findRedundantInstructions(a.rhs().size(), startState, test1, test2, branches, block, InterleavedBlock::rightInterleavedIndex);

				if (!redundantRight.isEmpty()) {
					test1[block] = a.without(redundantRight.stream().mapToInt(a::rightInterleavedIndex).toArray());
					test2[block] = b.without(redundantRight.stream().mapToInt(b::rightInterleavedIndex).toArray());

					changed = true;
					System.err.printf("Removed %d redundant instructions from block %d%n", redundantRight.size(), block);
					break;
				}
			}

			if (!changed) break;
		}
		System.out.println("Done minimising");

		return new InvarianceTestCase(tc.pairs(), tc.branches(), new ExecutableSequence(test1, branches), new ExecutableSequence(test2, branches), startState);
	}

	private ArrayList<Integer> findRedundantInstructions(int victimBlockSize, CPUState startState, Block[] test1, Block[] test2, Branch[] branches, int blockToReduce, BiFunction<InterleavedBlock, Integer, Integer> getVictimIndex) {
		var a = (InterleavedBlock) test1[blockToReduce];
		var b = (InterleavedBlock) test2[blockToReduce];

		assert a.size() == b.size();

		var redundantIndices = new ArrayList<Integer>();
		for (int i = 0; i < victimBlockSize; i++) {
			int test1Index = getVictimIndex.apply(a, i);
			int test2Index = getVictimIndex.apply(b, i);

			var aWithout = a.without(test1Index);
			var bWithout = b.without(test2Index);

			var test1Changed = new Block[test1.length];
			var test2Changed = new Block[test2.length];

			System.arraycopy(test1, 0, test1Changed, 0, test1.length);
			System.arraycopy(test2, 0, test2Changed, 0, test2.length);

			test1Changed[blockToReduce] = aWithout;
			test2Changed[blockToReduce] = bWithout;

			var test1Result = executor.runSequence(startState, new ExecutableSequence(test1Changed, branches));
			var test2Result = executor.runSequence(startState, new ExecutableSequence(test2Changed, branches));

			if (interestingMismatch(test1Result.result(), test2Result.result())) {
				redundantIndices.add(i);
			}
		}

		return redundantIndices;
	}

	private CPUState simplifyInitialState(CPUState state, ExecutableSequence... sequences) {
		for (int i = 0; i < 32; i++) {
			var zmm = state.zmm().withZeroed(i);
			if (zmm.equals(state.zmm())) continue;

			var newState = new CPUState(state.gprs(), zmm, state.mmx(), state.rflags());

			ExecutionResult previous = null;
			for (var sequence : sequences) {
				var result = executor.runSequence(newState, sequence).result();
				if (interestingMismatch(previous, result)) {
					return newState;
				}
				previous = result;
			}
		}

		for (int i = 0; i < 8; i++) {
			var mmx = state.mmx().withZeroed(i);
			if (mmx.equals(state.mmx())) continue;

			var newState = new CPUState(state.gprs(), state.zmm(), mmx, state.rflags());

			boolean consistent = true;
			ExecutionResult previous = null;
			for (var sequence : sequences) {
				var result = executor.runSequence(newState, sequence).result();
				if (interestingMismatch(previous, result)) {
					consistent = false;
					break;
				}
				previous = result;
			}

			if (!consistent) {
				return newState;
			}
		}

		for (int i = 0; i < 16; i++) {
			var gprs = state.gprs().withZeroed(i);
			if (gprs.equals(state.gprs())) continue;
			var newState = new CPUState(gprs, state.zmm(), state.mmx(), state.rflags());

			boolean consistent = true;
			ExecutionResult previous = null;
			for (var sequence : sequences) {
				var result = executor.runSequence(newState, sequence).result();
				if (interestingMismatch(previous, result)) {
					consistent = false;
					break;
				}
				previous = result;
			}

			if (!consistent) {
				return newState;
			}
		}

		return state;
	}
}
