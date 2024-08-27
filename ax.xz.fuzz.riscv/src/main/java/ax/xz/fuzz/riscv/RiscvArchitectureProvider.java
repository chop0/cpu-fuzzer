package ax.xz.fuzz.riscv;

import ax.xz.fuzz.arch.Architecture;
import ax.xz.fuzz.arch.ArchitectureProvider;
import ax.xz.fuzz.riscv.base.RiscvBaseModule;
import ax.xz.fuzz.riscv.m.RiscvMModule;

import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class RiscvArchitectureProvider implements ArchitectureProvider {
	@Override
	public Architecture getArchitecture() {
		return InstanceHolder.INSTANCE;
	}

	@Override
	public boolean isAvailable() {
		return System.getProperty("os.arch").equals("riscv64") &&
			System.getProperty("os.name").equals("Linux");
	}

	private static final class InstanceHolder {
		private static final RiscvArchitecture INSTANCE;

		static {
			try (var libraryPath = RiscvArchitectureProvider.class.getClassLoader().getResourceAsStream("libmain.riscv64.so")) {
				var tempFile = Files.createTempFile("libmain.riscv64", ".so");
				Files.copy(libraryPath, tempFile, StandardCopyOption.REPLACE_EXISTING);

				System.load(tempFile.toString());
				SymbolLookup.loaderLookup().find("trampoline_return_address").orElseThrow();
				Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
					try {
						Files.delete(tempFile);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}));
			} catch (Exception e) {
				throw new RuntimeException("Failed to load native library", e);
			}

			INSTANCE = new RiscvArchitecture(new RiscvBaseModule.RV64I(), RiscvMModule.rv64m());
			System.out.println("Loaded local uarch: " + INSTANCE);
		}
	}
}
