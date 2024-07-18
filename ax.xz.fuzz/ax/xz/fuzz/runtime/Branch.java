package ax.xz.fuzz.runtime;

public record Branch(ExecutableSequence.BranchType type, int takenIndex, int notTakenIndex) {
	@Override
	public String toString() {
		return """
			%s %d
			jmp %d""".formatted(type.name().toLowerCase(), takenIndex, notTakenIndex);
	}
}
