package ax.xz.fuzz.arch;

public interface ArchitectureProvider {
	    Architecture nativeArchitecture();
	    boolean isAvailable();
}
