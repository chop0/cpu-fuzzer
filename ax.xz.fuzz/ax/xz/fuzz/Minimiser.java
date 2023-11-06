package ax.xz.fuzz;

import com.github.icedland.iced.x86.Instruction;

import java.lang.reflect.Array;
import java.util.*;

public class Minimiser {
	public static void minimise(Runnable reset, CombinedBlock a, CombinedBlock b) throws CombinedBlock.UnencodeableException {
		if (a.lhs() != b.lhs() || a.rhs() != b.rhs())
			throw new IllegalArgumentException("a.lhs() != b.lhs() || a.rhs() != b.rhs()");

		var lhsSkips = new BitSet(a.lhs().size());
		var rhsSkips = new BitSet(a.rhs().size());

		outer: for (;;) {
			boolean changed = false;
			inner: for (int i = 0; i < a.lhs().size(); i++) {
				if (lhsSkips.get(i))
					continue inner;

				lhsSkips.set(i);
				reset.run();
				if (!checkMismatch(a, b, lhsSkips, rhsSkips)) {
					lhsSkips.clear(i);
				} else {
					System.out.println("Found redundant instruction in LHS at " + i);
					changed = true;
				}
			}

			for (int i = 0; i < a.rhs().size(); i++) {
				if (rhsSkips.get(i))
					continue ;

				rhsSkips.set(i);
				reset.run();
				if (!checkMismatch(a, b, lhsSkips, rhsSkips)) {
					rhsSkips.clear(i);
				} else {
					System.out.println("Found redundant instruction in RHS at " + i);
					changed = true;
				}
			}

			if (!changed)
				break ;
		}

		var aFinal = pick(a.picks(), a.lhs().instructions(), a.rhs().instructions(), lhsSkips, rhsSkips);
		var bFinal = pick(b.picks(), a.lhs().instructions(), a.rhs().instructions(), lhsSkips, rhsSkips);

		System.out.println("Minimised A:");
		System.out.println(Arrays.stream(aFinal)
				.map(Instruction::toString)
						.map(n -> {
							var split = n.split(" ", 2);
							var arguments = split.length > 1 ? split[1].replaceAll("([A-Fa-f0-9]{2,40})h", "0x$1") : "";
							arguments = arguments.replaceAll(" ptr", "_ptr");
							arguments = arguments.replace('[', '(');
							arguments = arguments.replace(']', ')');
							return "assembler." + split[0] + "(" + arguments + ");";
						})
				.reduce((a_, b_) -> a_ + "\n" + b_)
				.orElse(""));
		System.out.println("Minimised B:");
		System.out.println(Arrays.stream(bFinal)
				.map(Instruction::toString)
						.map(n -> {
							var split = n.split(" ", 2);
							var arguments = split.length > 1 ? split[1].replaceAll("([A-Fa-f0-9]{2,40})h", "0x$1") : "";
							arguments = arguments.replaceAll(" ptr", "_ptr");
							arguments = arguments.replace('[', '(');
							arguments = arguments.replace(']', ')');

							return "assembler." + split[0] + "(" + arguments + ");";
						})
				.reduce((a_, b_) -> a_ + "\n" + b_)
				.orElse(""));
	}

	private static boolean checkMismatch(CombinedBlock a, CombinedBlock b, BitSet lhsBitset, BitSet rhsBitset) throws CombinedBlock.UnencodeableException {
		var aMaskedInstructions = pick(a.picks(), a.lhs().instructions(), a.rhs().instructions(), lhsBitset, rhsBitset);
		var bMaskedInstructions = pick(b.picks(), a.lhs().instructions(), a.rhs().instructions(), lhsBitset, rhsBitset);

		var aMaskedOpcodes = pick(a.picks(), a.lhs().opcodes(), a.rhs().opcodes(), lhsBitset, rhsBitset);
		var bMaskedOpcodes = pick(b.picks(), a.lhs().opcodes(), a.rhs().opcodes(), lhsBitset, rhsBitset);

		var aResult = Tester.runBlock(CPUState.filledWith(0), aMaskedOpcodes, aMaskedInstructions);
		var bResult = Tester.runBlock(CPUState.filledWith(0), bMaskedOpcodes, bMaskedInstructions);

		return mismatch(aResult, bResult);
	}


	private static <T> T[] pick(BitSet picks, T[] lhs, T[] rhs, BitSet lhsSkip, BitSet rhsSkip) {
		var result = (T[]) Array.newInstance(lhs.getClass().getComponentType(), lhs.length + rhs.length);

		int lhsIndex = 0, rhsIndex = 0;
		for (int i = 0; i < result.length; i++) {
			if (rhsIndex == rhs.length || (lhsIndex < lhs.length && picks.get(i))) {
				result[i] = lhsSkip.get(lhsIndex) ? null : lhs[lhsIndex];
				lhsIndex++;
			}
			else {
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
