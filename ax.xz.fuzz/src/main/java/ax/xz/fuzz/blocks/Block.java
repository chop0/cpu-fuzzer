package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.InstructionBuilder;
import ax.xz.fuzz.instruction.Opcode;

import java.util.List;

public interface Block {
	default int size() {
		return items().size();
	}
	List<? extends BlockEntry> items();
	Block without(int index);
	int indexOf(BlockEntry entry);

	public static class UnencodeableException extends Exception {
		public final Opcode opcode;
		public final InstructionBuilder instruction;

		public UnencodeableException(Throwable cause, Opcode opcode, InstructionBuilder instruction) {
			super(cause);
			this.opcode = opcode;
			this.instruction = instruction;
		}
	}

}
