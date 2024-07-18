module ax.xz.fuzz {
	requires antlr;
	requires com.github.icedland.iced.x86;

	requires com.fasterxml.jackson.core;
	requires com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.annotation;
	requires jdk.httpserver;
	requires java.instrument;
	requires java.desktop;
	requires java.management;

	opens ax.xz.fuzz.encoding to com.fasterxml.jackson.databind;
	opens ax.xz.fuzz.blocks to com.fasterxml.jackson.databind;
	opens ax.xz.fuzz.instruction to com.fasterxml.jackson.databind;
	opens ax.xz.fuzz.blocks.randomisers to com.fasterxml.jackson.databind;
	opens ax.xz.fuzz.runtime to com.fasterxml.jackson.databind;
}