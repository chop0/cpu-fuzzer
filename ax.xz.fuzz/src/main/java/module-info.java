module ax.xz.fuzz {
	requires jdk.jfr;
	requires jdk.httpserver;
	requires com.fasterxml.jackson.dataformat.xml;
	requires com.fasterxml.jackson.databind;
	requires java.management;
	requires info.picocli;

	exports ax.xz.fuzz.blocks;
	exports ax.xz.fuzz.instruction;
	exports ax.xz.fuzz.mman;
	exports ax.xz.fuzz.arch;
	exports ax.xz.fuzz.runtime;
	exports ax.xz.fuzz.mutate;

	exports ax.xz.fuzz to info.picocli;

	uses ax.xz.fuzz.arch.ArchitectureProvider;
}