package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.Block;
import ax.xz.fuzz.blocks.InterleavedBlock;
import ax.xz.fuzz.tester.execution_result;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.BiFunction;

import static ax.xz.fuzz.runtime.MemoryUtils.assignPkey;
import static ax.xz.fuzz.runtime.Tester.SCRATCH_PKEY;
import static ax.xz.fuzz.tester.slave_h.*;
import static ax.xz.fuzz.tester.slave_h.do_test;
import static com.github.icedland.iced.x86.Register.R15;

public class Minimiser {
	public static void minimise(Runnable reset, Block[] test1, Block[] test2, TestCase.Branch[] branches) {
		for (;;) {
			boolean changed = false;

			for (int block = 0; block < test1.length; block++) {
				if (!(test1[block] instanceof InterleavedBlock a && test2[block] instanceof InterleavedBlock b)) {
					System.out.println("Skipping non-interleaved block");
					continue;
				}

				reset.run();
				var redundantLeft = findRedundantInstructions(a.lhs().size(), test1, test2, branches, block, InterleavedBlock::leftInterleavedIndex);

				if (!redundantLeft.isEmpty()) {
					test1[block] = a.without(redundantLeft.stream().mapToInt(a::leftInterleavedIndex).toArray());
					test2[block] = b.without(redundantLeft.stream().mapToInt(b::leftInterleavedIndex).toArray());

					changed = true;
					System.err.printf("Removed %d redundant instructions from block %d%n", redundantLeft.size(), block);
					break;
				}

				var redundantRight = findRedundantInstructions(a.rhs().size(), test1, test2, branches, block, InterleavedBlock::rightInterleavedIndex);

				if (!redundantRight.isEmpty()) {
					test1[block] = a.without(redundantRight.stream().mapToInt(a::rightInterleavedIndex).toArray());
					test2[block] = b.without(redundantRight.stream().mapToInt(b::rightInterleavedIndex).toArray());

					changed = true;
					System.err.printf("Removed %d redundant instructions from block %d%n", redundantRight.size(), block);
					break;
				}
			}

			if (!changed)
				break;
		}

		System.err.println("Minimised A:");
		System.err.println(new TestCase(test1, branches));
		System.err.println("Minimised B:");
		System.err.println(new TestCase(test2, branches));
	}

	private static ArrayList<Integer> findRedundantInstructions(int victimBlockSize, Block[] test1, Block[] test2, TestCase.Branch[] branches, int blockToReduce, BiFunction<InterleavedBlock, Integer, Integer> getVictimIndex) {
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

			var test1Result = run(new TestCase(test1Changed, branches));
			var test2Result = run(new TestCase(test2Changed, branches));

			if (mismatch(test1Result, test2Result)) {
				redundantIndices.add(i);
			}
		}

		return redundantIndices;
	}

	private static ExecutionResult run(TestCase tc) {
		try (var arena = Arena.ofConfined()) {
			var code = mmap(MemorySegment.NULL, 4096*16L, PROT_READ() | PROT_WRITE() | PROT_EXEC(), MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0)
					.reinterpret(4096 * 16, arena, ms -> munmap(ms, 4096 * 16L));
			assignPkey(code, SCRATCH_PKEY);

			var trampoline = Trampoline.create(arena);

			int length = tc.encode(code.address(), trampoline, code, R15, 100);

			var output = execution_result.allocate(arena);
			CPUState.filledWith(0).toSavedState(execution_result.state(output));
			do_test(SCRATCH_PKEY, trampoline.address(), code, length, output);

			return ExecutionResult.ofStruct(output);
		}
	}


	private static <T> T[] pick(BitSet picks, T[] lhs, T[] rhs, BitSet lhsSkip, BitSet rhsSkip) {
		var result = (T[]) Array.newInstance(lhs.getClass().getComponentType(), lhs.length + rhs.length);

		int lhsIndex = 0, rhsIndex = 0;
		for (int i = 0; i < result.length; i++) {
			if (rhsIndex == rhs.length || (lhsIndex < lhs.length && picks.get(i))) {
				result[i] = lhsSkip.get(lhsIndex) ? null : lhs[lhsIndex];
				lhsIndex++;
			} else {
				result[i] = rhsSkip.get(rhsIndex) ? null : rhs[rhsIndex];
				rhsIndex++;
			}
		}

		return Arrays.stream(result).filter(Objects::nonNull).toArray(n -> (T[]) Array.newInstance(lhs.getClass().getComponentType(), n));
	}

	private static boolean mismatch(ExecutionResult a, ExecutionResult b) {
		return ExecutionResult.interestingMismatch(a, b);
	}
}
