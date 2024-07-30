.PHONY: all clean

all: repro

repro: repro.S lib.c
	gcc -march=native -nostartfiles -g -o $@ $^

clean:
	rm repro
