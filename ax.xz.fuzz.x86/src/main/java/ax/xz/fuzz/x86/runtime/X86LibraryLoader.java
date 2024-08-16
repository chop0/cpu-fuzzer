package ax.xz.fuzz.x86.runtime;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

class X86LibraryLoader {
	static {
		try {
			var resources = X86LibraryLoader.class.getClassLoader().getResource("lib");
			var uri = resources.toURI();
			var loadCandidates = Files.walk(Path.of(uri), 2).filter(n -> !Files.isDirectory(n)).toList();

			for (Path loadCandidate : loadCandidates) {
				var tmp = Files.createTempFile("lib", ".so");
				Files.copy(loadCandidate, tmp, StandardCopyOption.REPLACE_EXISTING);

				try {
					System.load(tmp.toString());
					System.out.println("Loaded " + loadCandidate);
				} catch (UnsatisfiedLinkError e) {

				}
				Files.delete(tmp);
			}
		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();
		}
	}

	public static void load() {}
}
