package ax.xz.fuzz.instruction;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ax.xz.fuzz.arch.Architecture.activeArchitecture;

public final class RegisterSet implements Iterable<RegisterDescriptor> {
	public static RegisterSet EMPTY = new RegisterSet(new BitSet());

	public static RegisterSet of(RegisterDescriptor... registers) {
		if (registers.length == 0)
			return EMPTY;

		return Arrays.stream(registers).collect(collector());
	}

	private static RegisterSet of(BitSet enabled) {
		if (enabled.isEmpty())
			return EMPTY;

		return new RegisterSet( enabled);
	}

	public static Collector<? super RegisterDescriptor, BitSet, RegisterSet> collector() {
		return Collector.of(BitSet::new, (bitSet, registerDescriptor) -> {
			bitSet.set(activeArchitecture().registerIndex(registerDescriptor));
		}, (bs1, bs2) -> {
			bs1.or(bs2);
			return bs1;
		}, RegisterSet::new);
	}

	private final BitSet registers;

	private RegisterSet(BitSet registers) {
		this.registers = registers;
	}

	public boolean hasRegister(RegisterDescriptor register) {
		return registers.get(activeArchitecture().registerIndex(register));
	}

	public RegisterSet intersection(RegisterSet other) {
		if (isEmpty() || other.isEmpty())
			return EMPTY;

		var bs = new BitSet();
		bs.or(registers);
		bs.and(other.registers);

		return of(bs);
	}

	public boolean intersects(RegisterSet other) {
		if (isEmpty() || other.isEmpty())
			return false;

		return registers.intersects(other.registers);
	}

	public RegisterSet union(RegisterSet other) {
		if (isEmpty())
			return other;
		if (other.isEmpty())
			return this;

		var newSet = copyOf();
		newSet.registers.or(other.registers);

		return newSet;
	}

	public boolean contains(RegisterSet other) {
		if (isEmpty() || other.isEmpty())
			return false;

		return intersection(other).equals(other);
	}

	private RegisterSet copyOf() {
		if (isEmpty())
			return EMPTY;

		var bs = new BitSet();
		bs.or(registers);

		return of(bs);
	}

	public boolean isEmpty() {
		return this == EMPTY;
	}

	public RegisterDescriptor select(RandomGenerator randomGenerator) {
		if (isEmpty())
			throw new IllegalStateException("Cannot choose from empty register set");

		int currentIndex = registers.nextSetBit(0);
		int bound = randomGenerator.nextInt(registers.cardinality());

		for (int i = 0; i < bound; i++) {
			currentIndex = registers.nextSetBit(currentIndex + 1);
		}

		return activeArchitecture().registerByIndex(currentIndex);
	}

	public RegisterSet consecutiveBlocks(int blockSize, RegisterSet startRegisters) {
		var vectorBlocks = startRegisters.registers.stream()
			.filter(startIndex -> this.registers.nextClearBit(startIndex) - startIndex >= blockSize)
			.collect(BitSet::new, BitSet::set, BitSet::or);

		return of(vectorBlocks);
	}

	public Stream<RegisterDescriptor> stream() {
		return registers.stream().mapToObj(n -> activeArchitecture().registerByIndex(n));
	}

	public RegisterDescriptor first() {
		return activeArchitecture().registerByIndex(registers.nextSetBit(0));
	}

	public RegisterDescriptor last() {
		return activeArchitecture().registerByIndex(registers.previousSetBit(registers.length() - 1));
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (RegisterSet) obj;
		return Objects.equals(this.registers, that.registers);
	}

	@Override
	public int hashCode() {
		return registers.hashCode();
	}

	@Override
	public String toString() {
		return registers.stream()
			.sorted()
			.mapToObj(activeArchitecture()::registerByIndex)
			.map(n -> n.toString())
			.collect(Collectors.joining(", ", "{", "}"));
	}

	public RegisterSet subtract(RegisterSet rhs) {
		var newSet = copyOf();
		newSet.registers.andNot(rhs.registers);
		return newSet;
	}


	@Override
	public Iterator<RegisterDescriptor> iterator() {
		if (this == EMPTY)
			return Stream.<RegisterDescriptor>empty().iterator();

		return registers.stream().mapToObj(activeArchitecture()::registerByIndex).iterator();
	}
}
