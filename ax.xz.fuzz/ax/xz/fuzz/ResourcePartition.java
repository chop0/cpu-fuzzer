package ax.xz.fuzz;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.random.RandomGenerator;

import static java.lang.foreign.MemoryLayout.sequenceLayout;

public record ResourcePartition(EnumSet<StatusFlag> statusFlags, RegisterSet allowedRegisters,
								MemoryPartition[] allowedMemoryRanges) {
	public static ResourcePartition all(boolean evex) {
		return new ResourcePartition(StatusFlag.all(), evex ? RegisterSet.ALL_EVEX : RegisterSet.ALL_VEX, new MemoryPartition[]{new MemoryPartition(MemorySegment.ofAddress(0).reinterpret(Long.MAX_VALUE))});
	}

	public static ResourcePartition all(boolean evex, MemorySegment ms) {
		return new ResourcePartition(StatusFlag.all(), evex ? RegisterSet.ALL_EVEX : RegisterSet.ALL_VEX, new MemoryPartition[]{new MemoryPartition(ms)});
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
		if (rng.nextBoolean())
			Collections.addAll(statusFlags, StatusFlag.values());

		return new ResourcePartition[]{
				new ResourcePartition(statusFlags, new RegisterSet(lhsRegisters), new MemoryPartition[]{MemoryPartition.of(lhsMemory)}),
				new ResourcePartition(EnumSet.complementOf(statusFlags), new RegisterSet(rhsRegisters), new MemoryPartition[]{MemoryPartition.of(rhsMemory)})
		};
	}

	public long randomMemoryAddress(RandomGenerator random, int requiredSize, int alignment, int addressWidthBytes) throws BlockGenerator.NoPossibilitiesException {
		var allowedMemoryRanges = Arrays.stream(this.allowedMemoryRanges)
				.filter(n -> n.canFulfil(requiredSize, alignment, addressWidthBytes))
				.toArray(MemoryPartition[]::new);

		if (allowedMemoryRanges.length == 0)
			throw new BlockGenerator.NoPossibilitiesException();

		var range = allowedMemoryRanges[random.nextInt(allowedMemoryRanges.length)];
		return range.randomPosition(random, requiredSize, alignment, addressWidthBytes);
	}

	public int selectRegister(RegisterSet desiredResources, RandomGenerator random) throws BlockGenerator.NoPossibilitiesException {
		var intersection = allowedRegisters.intersection(desiredResources);

		if (intersection.isEmpty())
			throw new BlockGenerator.NoPossibilitiesException();

		return intersection.choose(random);
	}

	public boolean canFulfil(int requiredSize, int alignment, int addressWidthBytes) {
		return Arrays.stream(this.allowedMemoryRanges).anyMatch(n -> n.canFulfil(requiredSize, alignment, addressWidthBytes));
	}

	public ResourcePartition withAllowedRegisters(RegisterSet allowedRegisters) {
		return new ResourcePartition(statusFlags, allowedRegisters, allowedMemoryRanges);
	}
}
