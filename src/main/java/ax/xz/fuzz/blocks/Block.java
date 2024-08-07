package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.Opcode;
import com.github.icedland.iced.x86.Instruction;

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
		public final Instruction instruction;

		public UnencodeableException(Throwable cause, Opcode opcode, Instruction instruction) {
			super(cause);
			this.opcode = opcode;
			this.instruction = instruction;
		}
	}

}
