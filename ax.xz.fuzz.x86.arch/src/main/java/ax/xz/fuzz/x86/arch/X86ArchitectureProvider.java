package ax.xz.fuzz.x86.arch;

import ax.xz.fuzz.arch.Architecture;
import ax.xz.fuzz.arch.ArchitectureProvider;

public final class X86ArchitectureProvider implements ArchitectureProvider {
	@Override
	public Architecture nativeArchitecture() {
		return X86Architecture.nativeArchitecture();
	}

	@Override
	public boolean isAvailable() {
		return System.getProperty("os.arch").equals("amd64")
			&& System.getProperty("os.name").equals("Linux");
	}
}
