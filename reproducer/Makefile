.PHONY: all clean

all: repro2
	./repro2

repro2: repro2.o lib.o
	gcc -march=native -pthread -g -o $@ $^ -nostartfiles

repro2.o: repro2.S
	gcc -DDEBUG -c repro2.S -o $@

repro: repro.o lib.o
	gcc -march=native -pthread -g -o $@ $^ -nostartfiles

repro.o: repro.S
	gcc -DDEBUG -c repro.S -o $@

lib.o: lib.c
	gcc -DDEBUG -march=native -pthread -g -c -o $@ $^

clean:
	rm -f repro *.o
