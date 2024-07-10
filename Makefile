ANTLR_JAR = antlr-4.13.1-complete.jar
MODULE_PATH = $(ANTLR_JAR):iced-x86-1.20.0.jar:jackson-annotations-2.16.0-rc1.jar:jackson-core-2.16.0-rc1.jar:jackson-databind-2.16.0-rc1.jar

run: ax.xz.fuzz libslave.so
	java --enable-native-access=ax.xz.fuzz --enable-preview -Djava.library.path=. -p $(MODULE_PATH):out -m ax.xz.fuzz/ax.xz.fuzz.runtime.Master

profile: ax.xz.fuzz libslave.so
	java --enable-native-access=ax.xz.fuzz -agentpath:/home/apetri/usr/lib/libasyncProfiler.so=start,event=cpu,file=profile.html,cstack=dwarf --enable-preview -Djava.library.path=. -p $(MODULE_PATH):out -m ax.xz.fuzz/ax.xz.fuzz.runtime.Master


all: libslave.so ax.xz.fuzz

libslave.so: test_entry_routine.S slave.c segfault_handler.S
	gcc -g  -Wl,-z,relro,-z,now,-fPIC -fPIC -shared -O0 -o $@ $^

cpu-fuzzer.tar.gz: ax.xz.fuzz libslave.so
	jlink --module-path $(MODULE_PATH):out --add-modules ax.xz.fuzz --output cpu-fuzzer --launcher ax.xz.fuzz=ax.xz.fuzz/ax.xz.fuzz.runtime.Master --output cpu-fuzzer
	cp libslave.so cpu-fuzzer/lib
	cp instructions.xml cpu-fuzzer
	cp instructions.json cpu-fuzzer
	tar -czf $@ cpu-fuzzer

ax.xz.fuzz: $(shell find ax.xz.fuzz -name "*.java") parse bindings
	javac --release 22 --enable-preview -p $(MODULE_PATH) -d out --module ax.xz.fuzz --module-source-path .:gen

bindings: slave.h jextract_exports.txt
	jextract  -t ax.xz.fuzz.tester slave.h --output gen/ax.xz.fuzz @jextract_exports.txt

parse: Operand.g4
	java -jar $(ANTLR_JAR) -Dlanguage=Java -o gen/ax.xz.fuzz/ax/xz/fuzz/parse -package ax.xz.fuzz.parse $^

clean:
	rm -rf out/* libslave.so ax.xz.fuzz/ax/xz/fuzz/parse ax.xz.fuzz/ax/xz/fuzz/tester