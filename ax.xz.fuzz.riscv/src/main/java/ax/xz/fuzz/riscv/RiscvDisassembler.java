package ax.xz.fuzz.riscv;

import ax.xz.fuzz.riscv.base.RiscvBaseOpcode;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static ax.xz.fuzz.riscv.RiscvInstructionField.OPCODE;
import static ax.xz.fuzz.riscv.base.RiscvBaseField.FUNCT3;
import static ax.xz.fuzz.riscv.base.RiscvBaseField.IMM_B;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

public class RiscvDisassembler {
	private final RiscvArchitecture architecture;

	public RiscvDisassembler(RiscvArchitecture architecture) {
		this.architecture = architecture;
	}

	private RiscvOpcode findOpcode(int instruction) {
		var results = Arrays.stream(architecture.allOpcodes())
			.map(RiscvOpcode.class::cast)
			.filter(n -> n.isInstance(instruction))
			.toList();

		if (results.isEmpty()) {
			var opcode = OPCODE.get(instruction);

			throw new IllegalArgumentException("Unknown instructions: %04x (opcode 0b%s)"
				.formatted(instruction, Integer.toBinaryString(opcode)));
		} else if (results.size() > 1) {
			throw new IllegalArgumentException("Ambiguous instruction: %s.  Possibilities: %s".formatted(Integer.toHexString(instruction), results));
		} else {
			return results.getFirst();
		}
	}

	public String disassemble(int instruction) {
		return findOpcode(instruction).disassemble(instruction);
	}

	public String disassemble(byte[] instructions) {
		var label = 0;
		var labels = new HashMap<Integer, String>();
		var entries = new ArrayList<String>();

		if (instructions.length % 4 != 0) {
			throw new IllegalArgumentException("Instruction length must be a multiple of 4");
		}

		for (var i = 0; i < instructions.length; i += 4) {
			var instructionInt = ByteBuffer.wrap(instructions, i, 4).order(LITTLE_ENDIAN).getInt();
			var opcode = findOpcode(instructionInt);

			if (opcode instanceof RiscvBaseOpcode.BranchOpcode b) {
				var target = i + IMM_B.getSigned(instructionInt);
				var labelName = labels.computeIfAbsent(target/4, t -> "label" + t);

				entries.add(b.disassemble(instructionInt, labelName));
			}
			else
				entries.add(opcode.disassemble(instructionInt));
		}

		var result = new StringBuilder();
		for (var i = 0; i < entries.size(); i++) {
			var entry = entries.get(i);
			var labelName = labels.get(i);
			if (labelName != null) {
				result.append("\n").append(labelName).append(":\n");

			}
			result.append("\t").append(entry).append("\n");
		}

		return result.toString();
	}

}
