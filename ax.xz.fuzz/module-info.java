module ax.xz.fuzz {
	requires antlr;
	requires com.github.icedland.iced.x86;

	requires java.xml;

	requires com.fasterxml.jackson.core;
	requires com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.annotation;

	opens ax.xz.fuzz.encoding to com.fasterxml.jackson.databind;
}