ANTLR_JAR = antlr-4.13.1-complete.jar
MODULE_PATH = $(ANTLR_JAR):iced-x86-1.20.0.jar:jackson-annotations-2.16.0-rc1.jar:jackson-core-2.16.0-rc1.jar:jackson-databind-2.16.0-rc1.jar

run: ax.xz.fuzz libslave.so
	java --enable-preview -Djava.library.path=. -p $(MODULE_PATH):out -m ax.xz.fuzz/ax.xz.fuzz.Main

all: libslave.so ax.xz.fuzz

libslave.so: test_entry_routine.S slave.c
	gcc  -Wl,-z,relro,-z,now -fPIC -shared -o $@ $^

ax.xz.fuzz: $(shell find ax.xz.fuzz -name "*.java") parse bindings
	javac -p $(MODULE_PATH) -d out --module ax.xz.fuzz --module-source-path .:gen

bindings: slave.h jextract_exports.txt
	jextract  -t ax.xz.fuzz.tester slave.h --output gen/ax.xz.fuzz @jextract_exports.txt

parse: Operand.g4
	java -jar $(ANTLR_JAR) -Dlanguage=Java -o gen/ax.xz.fuzz/ax/xz/fuzz/parse -package ax.xz.fuzz.parse $^

clean:
	rm -rf out/* libslave.so ax.xz.fuzz/ax/xz/fuzz/parse ax.xz.fuzz/ax/xz/fuzz/tester