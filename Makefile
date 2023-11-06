ANTLR_JAR = antlr-4.13.1-complete.jar
MODULE_PATH = $(ANTLR_JAR):iced-x86-1.20.0.jar

.PHONY: all clean run parse bindings ax.xz.fuzz
run: ax.xz.fuzz libslave.so
	java --enable-preview -Djava.library.path=. -p $(MODULE_PATH):out -m ax.xz.fuzz/ax.xz.fuzz.Main

all: libslave.so ax.xz.fuzz

libslave.so: test_entry_routine.S slave.c slave.h
	gcc -fPIC -shared -o $@ $^

ax.xz.fuzz: $(shell find . -name "*.java") parse bindings
	javac --enable-preview --source 21 -p $(MODULE_PATH) -d out --module ax.xz.fuzz --module-source-path .:gen

bindings: slave.h jextract_exports.txt
	jextract -l slave -t ax.xz.fuzz.tester --source slave.h --output gen/ax.xz.fuzz @jextract_exports.txt

parse: Operand.g4
	java -jar $(ANTLR_JAR) -Dlanguage=Java -o gen/ax.xz.fuzz/ax/xz/fuzz/parse -package ax.xz.fuzz.parse Operand.g4

clean:
	rm -rf out/* libslave.so ax.xz.fuzz/ax/xz/fuzz/parse ax.xz.fuzz/ax/xz/fuzz/tester