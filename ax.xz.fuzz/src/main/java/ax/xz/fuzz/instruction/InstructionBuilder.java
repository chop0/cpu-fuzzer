package ax.xz.fuzz.instruction;

public interface InstructionBuilder {
	byte[] encode(long pc);
}
