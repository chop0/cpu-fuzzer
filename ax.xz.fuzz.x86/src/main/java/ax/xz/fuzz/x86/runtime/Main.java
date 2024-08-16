package ax.xz.fuzz.x86.runtime;

import ax.xz.xed.XedInstruction;
import ax.xz.xed.XedInstructions;

public class Main {
	public static void main(String[] args) {
		var instructions = XedInstructions.instructions();
		var checker = new X86InstructionSupportChecker();

		for (XedInstruction instruction : instructions) {
			if (checker.isSupported(instruction))
				System.out.println(instruction);
		}
	}
}
