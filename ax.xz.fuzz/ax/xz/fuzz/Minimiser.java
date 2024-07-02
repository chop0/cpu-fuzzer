package ax.xz.fuzz;

import ax.xz.fuzz.tester.execution_result;
import com.github.icedland.iced.x86.Instruction;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;

import static ax.xz.fuzz.tester.slave_h.*;
import static ax.xz.fuzz.tester.slave_h.do_test;
import static com.github.icedland.iced.x86.Register.R15;

public class Minimiser {
	public static void minimise(int seed, Runnable reset, InterleavedBlock[] test1, InterleavedBlock[] test2, TestCase.Branch[] branches) throws BasicBlock.UnencodeableException {
		var rng = new Random();

		for (;;) {
			boolean changed = false;

			for (int block = 0; block < test1.length; block++) {
				var a = test1[block];
				var b = test2[block];

				for (int i = 0; i < a.lhsIndices().length; i++) {
					reset.run();

					int test1Index = a.lhsIndices()[i];
					int test2Index = b.lhsIndices()[i];

					var aWithout = a.without(test1Index);
					var bWithout = b.without(test2Index);

					var test1Changed = test1.clone();
					var test2Changed = test2.clone();

					test1Changed[block] = aWithout;
					test2Changed[block] = bWithout;

					rng.setSeed(seed);
					var test1Result = run(rng, new TestCase(test1Changed, branches));
					rng.setSeed(seed);
					var test2Result = run(rng, new TestCase(test2Changed, branches));

					if (mismatch(test1Result, test2Result)) {
						test1[block] = aWithout;
						test2[block] = bWithout;
						System.out.println("Found redundant instruction in LHS at " + i);
						changed = true;
					}
				}

				for (int i = 0; i < a.rhsIndices().length; i++) {
					reset.run();

					int test1Index = a.rhsIndices()[i];
					int test2Index = b.rhsIndices()[i];

					var aWithout = a.without(test1Index);
					var bWithout = b.without(test2Index);

					var test1Changed = test1.clone();
					var test2Changed = test2.clone();

					test1Changed[block] = aWithout;
					test2Changed[block] = bWithout;

					rng.setSeed(seed);
					var test1Result = run(rng, new TestCase(test1Changed, branches));
					rng.setSeed(seed);
					var test2Result = run(rng, new TestCase(test2Changed, branches));

					if (mismatch(test1Result, test2Result)) {
						test1[block] = aWithout;
						test2[block] = bWithout;
						System.out.println("Found redundant instruction in LHS at " + i);
						changed = true;
					}
				}
			}

			if (!changed)
				break;
		}

		System.out.println("Minimised A:");
		System.out.println(new TestCase(test1, branches));
		System.out.println("Minimised B:");
		System.out.println(new TestCase(test2, branches));
	}

	private static ExecutionResult run(RandomGenerator rng, TestCase tc) {
		try (var arena = Arena.ofConfined()) {
			var code = mmap(MemorySegment.NULL, 4096*16L, PROT_READ() | PROT_WRITE() | PROT_EXEC(), MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0)
					.reinterpret(4096 * 16, arena, ms -> munmap(ms, 4096 * 16L));

			var buf = code.asByteBuffer();
			 tc.encode(rng, code.address(), buf::put, R15, 100);

			var output = execution_result.allocate(arena);
			CPUState.filledWith(0).toSavedState(execution_result.state(output));
			do_test(code, buf.position(), output);

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
		return (a instanceof ExecutionResult.Fault) != (b instanceof ExecutionResult.Fault)
			   || ((a instanceof ExecutionResult.Success || b instanceof ExecutionResult.Success) && !a.equals(b));
	}
}
