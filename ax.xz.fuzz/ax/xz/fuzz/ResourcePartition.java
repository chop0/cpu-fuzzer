package ax.xz.fuzz;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.random.RandomGenerator;

import static java.lang.foreign.MemoryLayout.sequenceLayout;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public record ResourcePartition(EnumSet<StatusFlag> statusFlags, RegisterSet allowedRegisters,
								MemoryPartition[] allowedMemoryRanges) {
	public static ResourcePartition all(boolean evex) {
		return new ResourcePartition(StatusFlag.all(), evex ? RegisterSet.ALL_EVEX : RegisterSet.ALL_VEX, new MemoryPartition[]{new MemoryPartition(MemorySegment.ofAddress(0).reinterpret(Long.MAX_VALUE))});
	}

	public static ResourcePartition ofMemory(boolean evex, MemorySegment memory) {
		return new ResourcePartition(StatusFlag.all(), evex ? RegisterSet.ALL_EVEX : RegisterSet.ALL_VEX, new MemoryPartition[]{MemoryPartition.of(memory)});
	}

	public static ResourcePartition[] partitioned(boolean evex, RandomGenerator rng, MemorySegment memory) {
		var lhsMemory = new ArrayList<MemorySegment>();
		var rhsMemory = new ArrayList<MemorySegment>();

		lhsMemory.add(memory.asSlice(0, memory.byteSize() / 4));
		rhsMemory.add(memory.asSlice(memory.byteSize() / 4, memory.byteSize() / 4));

		memory = memory.asSlice(0, memory.byteSize() / 2);

		memory.spliterator(sequenceLayout(64, JAVA_BYTE)).forEachRemaining(segment -> {
			if (rng.nextBoolean())
				lhsMemory.add(segment);
			else
				rhsMemory.add(segment);
		});

		var lhsRegisters = new BitSet();
		var rhsRegisters = new BitSet();

		var universe = evex ? RegisterSet.ALL_EVEX : RegisterSet.ALL_VEX;
		for (int[] bank : RegisterSet.banks) {
			var dst = rng.nextBoolean() ? lhsRegisters : rhsRegisters;
			for (int n : bank) {
				if (universe.hasRegister(n))
					dst.set(n);
			}
		}

		var lhsFlags = EnumSet.noneOf(StatusFlag.class);
		for (var flag : StatusFlag.values()) {
			if (rng.nextBoolean())
				lhsFlags.add(flag);
		}

		return new ResourcePartition[]{
				new ResourcePartition(lhsFlags, new RegisterSet(lhsRegisters), lhsMemory.stream().map(MemoryPartition::of).toArray(MemoryPartition[]::new)),
				new ResourcePartition(EnumSet.complementOf(lhsFlags), new RegisterSet(rhsRegisters), rhsMemory.stream().map(MemoryPartition::of).toArray(MemoryPartition[]::new))
		};
	}

	public static ResourcePartition[] partitioned(boolean evex, RandomGenerator rng, MemorySegment lhsMemory, MemorySegment rhsMemory) {
		var lhsRegisters = new BitSet();
		var rhsRegisters = new BitSet();

		var universe = evex ? RegisterSet.ALL_EVEX : RegisterSet.ALL_VEX;
		for (int[] bank : RegisterSet.banks) {
			var dst = rng.nextBoolean() ? lhsRegisters : rhsRegisters;
			for (int n : bank) {
				if (universe.hasRegister(n))
					dst.set(n);
			}
		}

		var statusFlags = EnumSet.noneOf(StatusFlag.class);
		for (var flag : StatusFlag.values()) {
			if (rng.nextBoolean())
				statusFlags.add(flag);
		}

		return new ResourcePartition[]{
				new ResourcePartition(statusFlags, new RegisterSet(lhsRegisters), new MemoryPartition[]{MemoryPartition.of(lhsMemory)}),
				new ResourcePartition(EnumSet.complementOf(statusFlags), new RegisterSet(rhsRegisters), new MemoryPartition[]{MemoryPartition.of(rhsMemory)})
		};
	}

	public long randomMemoryAddress(RandomGenerator random, int requiredSize, int alignment, int addressWidthBytes) throws InstructionGenerator.NoPossibilitiesException {
		var allowedMemoryRanges = Arrays.stream(this.allowedMemoryRanges)
				.filter(n -> n.canFulfil(requiredSize, alignment, addressWidthBytes))
				.toArray(MemoryPartition[]::new);

		if (allowedMemoryRanges.length == 0)
			throw new InstructionGenerator.NoPossibilitiesException();

		var range = allowedMemoryRanges[random.nextInt(allowedMemoryRanges.length)];
		return range.randomPosition(random, requiredSize, alignment, addressWidthBytes);
	}

	public int selectRegister(RegisterSet desiredResources, RandomGenerator random) throws InstructionGenerator.NoPossibilitiesException {
		var intersection = allowedRegisters.intersection(desiredResources);

		if (intersection.isEmpty())
			throw new InstructionGenerator.NoPossibilitiesException();

		return intersection.choose(random);
	}

	public boolean canFulfil(int requiredSize, int alignment, int addressWidthBytes) {
		return Arrays.stream(this.allowedMemoryRanges).anyMatch(n -> n.canFulfil(requiredSize, alignment, addressWidthBytes));
	}

	public ResourcePartition withAllowedRegisters(RegisterSet allowedRegisters) {
		return new ResourcePartition(statusFlags, allowedRegisters, allowedMemoryRanges);
	}
}
