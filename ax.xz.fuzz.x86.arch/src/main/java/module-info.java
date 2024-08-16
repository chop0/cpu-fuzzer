import ax.xz.fuzz.arch.ArchitectureProvider;
import ax.xz.fuzz.x86.arch.X86ArchitectureProvider;

module ax.xz.fuzz.x86.arch {
	requires ax.xz.fuzz;
	requires com.fasterxml.jackson.databind;
	requires com.github.icedland.iced.x86;
	requires java.xml;
	requires org.antlr.antlr4.runtime;

	exports ax.xz.fuzz.x86.operand;
	exports ax.xz.fuzz.x86.arch;

	provides ArchitectureProvider with X86ArchitectureProvider;
}