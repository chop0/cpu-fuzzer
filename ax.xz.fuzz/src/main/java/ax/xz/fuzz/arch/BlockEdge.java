package ax.xz.fuzz.arch;

import static ax.xz.fuzz.arch.Architecture.activeArchitecture;

public record BlockEdge(BranchDescription type, int takenIndex, int notTakenIndex) {
	@Override
	public String toString() {
		return type.asAssembler(takenIndex, notTakenIndex);
	}
}
