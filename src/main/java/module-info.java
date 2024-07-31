module ax.xz.fuzz {
	requires com.github.icedland.iced.x86;

	requires jdk.httpserver;
	requires java.instrument;
	requires java.desktop;
	requires java.management;
	requires com.fasterxml.jackson.dataformat.xml;
	requires com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.core;
	requires org.antlr.antlr4.runtime;
	requires info.picocli;
	requires jdk.jfr;


	opens ax.xz.fuzz.blocks to com.fasterxml.jackson.databind;
	opens ax.xz.fuzz.instruction to com.fasterxml.jackson.databind;
	opens ax.xz.fuzz.blocks.randomisers to com.fasterxml.jackson.databind;
	opens ax.xz.fuzz.runtime to com.fasterxml.jackson.databind, info.picocli;
	opens ax.xz.fuzz to com.fasterxml.jackson.databind;
	opens ax.xz.fuzz.metrics to com.fasterxml.jackson.databind;
}