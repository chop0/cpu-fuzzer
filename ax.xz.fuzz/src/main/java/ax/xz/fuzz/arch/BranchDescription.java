package ax.xz.fuzz.arch;

public interface BranchDescription {
	String asAssembler(int takenIndex, int notTakenIndex);
}
