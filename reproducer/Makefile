.PHONY: all clean libslave.so

all: repro
	./repro

repro: repro.c libslave.so
	gcc -g -o repro repro.c -I.. -L. -lslave -Wl,-rpath,.

libslave.so:
	make -C .. libslave.so
	cp ../libslave.so .

clean:
	rm -f repro libslave.so
	make -C .. clean