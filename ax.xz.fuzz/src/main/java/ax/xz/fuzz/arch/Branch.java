package ax.xz.fuzz.arch;

import static ax.xz.fuzz.arch.Architecture.getArchitecture;

public record Branch(BranchType type, int takenIndex, int notTakenIndex) {
	@Override
	public String toString() {
		return """
			%s %d
			%s %d""".formatted(type.name().toLowerCase(), takenIndex, getArchitecture().unconditionalJump(), notTakenIndex);
	}
}
