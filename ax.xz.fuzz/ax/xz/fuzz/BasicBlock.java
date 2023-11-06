package ax.xz.fuzz;

import com.github.icedland.iced.x86.Instruction;

import java.util.Arrays;

public record BasicBlock(Opcode[] opcodes, Instruction[] instructions) {
	public BasicBlock {
		if (opcodes.length != instructions.length)
			throw new IllegalArgumentException("opcodes.length != instructions.length");
	}
	int size() {
		return instructions.length;
	}

	@Override
	public String toString() {
		return Arrays.stream(instructions)
				.map(Instruction::toString)
				.reduce((a, b) -> a + "\n" + b)
				.orElse("");
	}
}
