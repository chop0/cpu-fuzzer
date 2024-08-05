package ax.xz.fuzz.reproduce;

import ax.xz.fuzz.blocks.Block;
import ax.xz.fuzz.blocks.BlockEntry;
import ax.xz.fuzz.blocks.InvarianceTestCase;
import ax.xz.fuzz.runtime.*;
import ax.xz.fuzz.runtime.state.CPUState;

import java.util.*;

import static ax.xz.fuzz.runtime.ExecutableSequence.BranchType.JMP;

public class Minimiser {
	private final SequenceExecutor executor;
	private final int attempts;

	public Minimiser(SequenceExecutor executor, int attempts) {
		this.executor = executor;
		this.attempts = attempts;
	}

	public TestCase minimise(TestCase tc) {
		for (; ; ) {
			if (simplifyRegistersOnce(tc)) {
				System.out.println("Simplified initial state");
				continue;
			}

			if (simplifyBranches(tc)) {
				System.out.println("Simplified branches");
				tc = pruneUnusedBlocks0(tc);
				continue;
			}

			if (simplifyBlocksPass(tc)) {
				System.out.println("Simplified blocks");
				continue;
			}

			break;
		}

		System.out.println("Done minimising");
		return tc;
	}

	private boolean simplifyBranches(TestCase tc) {
		for (int i = 0; i < tc.branches().length; i++) {
			if (simplifyBranch(tc, i)) {
				return true;
			}
		}

		return false;
	}

	private boolean simplifyBlocksPass( TestCase tc) {
		for (int i = 0; i < tc.blocksA().length; i++) {
			if (simplifyBlockOnce(tc, i)) {
				return true;
			}
		}

		return false;
	}

	private boolean simplifyBranch(TestCase tc, int branchIndex) {
		int end = tc.blocksA().length;
		var oldBranch = tc.branches()[branchIndex];
		tc.branches()[branchIndex] = new Branch(JMP, end, end);
		if (!tc.branches()[branchIndex].equals(oldBranch) && executor.lookForMismatch(tc, attempts)) {
			return true;
		}

		tc.branches()[branchIndex] = new Branch(JMP, oldBranch.takenIndex(), oldBranch.notTakenIndex());
		if (!tc.branches()[branchIndex].equals(oldBranch) && executor.lookForMismatch(tc, attempts)) {
			return true;
		}

		tc.branches()[branchIndex] = new Branch(JMP, oldBranch.notTakenIndex(), oldBranch.takenIndex());
		if (!tc.branches()[branchIndex].equals(oldBranch) && executor.lookForMismatch(tc, attempts)) {
			return true;
		}

		tc.branches()[branchIndex] = oldBranch;
		return false;
	}

	private boolean simplifyBlockOnce(TestCase tc, int blockIndex) {
		var lhsBlock = tc.blocksA()[blockIndex];
		var rhsBlock = tc.blocksB()[blockIndex];

		assert lhsBlock.size() == rhsBlock.size();

		List<? extends BlockEntry> items = lhsBlock.items();
		for (int i = 0; i < items.size(); i++) {
			var testEntry = items.get(i);

			int indexLhs = lhsBlock.indexOf(testEntry);
			int indexRhs = rhsBlock.indexOf(testEntry);

			var lhsWithout = lhsBlock.without(indexLhs);
			var rhsWithout = rhsBlock.without(indexRhs);

			tc.blocksA()[blockIndex] = lhsWithout;
			tc.blocksB()[blockIndex] = rhsWithout;

			if (executor.lookForMismatch(tc, attempts)) {
				return true;
			}

			tc.blocksA()[blockIndex] = lhsBlock;
			tc.blocksB()[blockIndex] = rhsBlock;
		}

		return false;
	}

	private static byte[][] removeInstruction(byte[][] original, byte[] instruction) {
		var newInstructions = new byte[original.length - 1][];
		int j = 0;
		for (byte[] instr : original) {
			if (!Arrays.equals(instr, instruction)) {
				newInstructions[j++] = instr;
			}
		}
		return newInstructions;
	}

	private boolean simplifyRegistersOnce(TestCase tc) {
		if (simplifyGprsOnce(tc)) {
			return true;
		}

		if (simplifyZmmOnce(tc)) {
			return true;
		}

		if (simplifyMmxOnce(tc)) {
			return true;
		}

		return false;
	}

	private boolean simplifyGprsOnce(TestCase tc) {
		var startState = tc.initialState();

		var gprValues = startState.gprs().values();
		for (int i = 0; i < gprValues.length; i++) {
			if (gprValues[i] == 0)
				continue;

			long oldValue = gprValues[i];
			gprValues[i] = 0;

			if (executor.lookForMismatch(tc, attempts)) {
				return true;
			}

			gprValues[i] = oldValue;
		}

		return false;
	}

	private void findVisitedBranches(Branch[] branches, int currentIndex, HashSet<Integer> visited) {
		if (visited.contains(currentIndex)) {
			return;
		}
		visited.add(currentIndex);
		findVisitedBranches(branches, branches[currentIndex].takenIndex(), visited);
		findVisitedBranches(branches, branches[currentIndex].notTakenIndex(), visited);
	}

	private TestCase pruneUnusedBlocks0(TestCase tc) {
		var blocksA = tc.blocksA();
		var blocksB = tc.blocksB();

		var visited = new HashSet<Integer>();
		findVisitedBranches(tc.branches(), 0, visited);

		var originalBlockLocationsA = new HashMap<Integer, Block>();
		var originalBlockLocationsB = new HashMap<Integer, Block>();
		for (int i = 0; i < blocksA.length; i++) {
			originalBlockLocationsA.put(i, blocksA[i]);
			originalBlockLocationsB.put(i, blocksB[i]);
		}

		var newBlocksA = new ArrayList<Block>();
		var newBlocksB = new ArrayList<Block>();


		for (int i = 0; i < blocksA.length; i++) {
			if (visited.contains(i)) {
				newBlocksA.add(originalBlockLocationsA.get(i));
				newBlocksB.add(originalBlockLocationsB.get(i));
			} else {
				System.out.println("Pruned block " + i);
			}
		}

		// now, we need to relocate the branches

		var newBranches = new ArrayList<Branch>();
		for (int i = 0; i < tc.branches().length; i++) {
			if (visited.contains(i)) {
				var branch = tc.branches()[i];
				var newTaken = newBlocksA.indexOf(originalBlockLocationsA.get(branch.takenIndex()));
				var newNotTaken = newBlocksA.indexOf(originalBlockLocationsA.get(branch.notTakenIndex()));
				newBranches.add(new Branch(branch.type(), newTaken, newNotTaken));
			}
		}

		var blocksAArray = newBlocksA.toArray(Block[]::new);
		var blocksBArray = newBlocksB.toArray(Block[]::new);
		var newBranchesArray = newBranches.toArray(Branch[]::new);
		return switch (tc) {
			case InvarianceTestCase(_, _, _, var state) -> new InvarianceTestCase(newBranchesArray, new ExecutableSequence(blocksAArray, newBranchesArray), new ExecutableSequence(blocksBArray, newBranchesArray), state);
			case RecordedTestCase(var initialState, var _, var _, var codeLocation, var _, var memory) -> new RecordedTestCase(initialState, blocksAArray, blocksBArray, codeLocation, newBranchesArray, memory);
		};
	}

	private boolean simplifyZmmOnce(TestCase tc) {
		var startState = tc.initialState();

		var zmmValues = startState.zmm().zmm();
		for (int i = 0; i < zmmValues.length; i++) {
			if (isZeros(zmmValues[i]))
				continue;

			var oldValue = zmmValues[i];
			zmmValues[i] = new byte[64];

			if (executor.lookForMismatch(tc, attempts)) {
				return true;
			}

			zmmValues[i] = oldValue;
		}

		return false;
	}

	private boolean simplifyMmxOnce(TestCase tc) {
		var startState = tc.initialState();

		var mmxValues = startState.mmx().mm();
		for (int i = 0; i < mmxValues.length; i++) {
			if (mmxValues[i] == 0)
				continue;

			var oldValue = mmxValues[i];
			mmxValues[i] = 0;

			if (executor.lookForMismatch(tc, attempts)) {
				return true;
			}

			mmxValues[i] = oldValue;
		}

		return false;
	}

	private static boolean isZeros(byte[] arr) {
		for (byte b : arr) {
			if (b != 0) {
				return false;
			}
		}
		return true;
	}
}
