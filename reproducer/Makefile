.PHONY: all clean

all: repro
	./repro

repro: repro.c
	gcc -g -o repro repro.c -march=native

clean:
	rm -f repro
