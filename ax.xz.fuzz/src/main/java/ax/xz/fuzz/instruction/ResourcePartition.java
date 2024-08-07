package ax.xz.fuzz.instruction;

import ax.xz.fuzz.blocks.NoPossibilitiesException;

import java.lang.foreign.MemorySegment;
import java.util.*;
import java.util.random.RandomGenerator;

public record ResourcePartition( EnumSet<StatusFlag> statusFlags, RegisterSet allowedRegisters, MemoryPartition memory, MemorySegment stack) {
	public ResourcePartition {
		if (statusFlags == null)
			statusFlags = EnumSet.noneOf(StatusFlag.class);
		if (allowedRegisters == null)
			allowedRegisters = RegisterSet.of();
		if (memory == null)
			memory = MemoryPartition.empty();
	}

	public long selectAddress(RandomGenerator random, int requiredSize, int alignment, int addressWidthBytes) throws NoPossibilitiesException {
		return memory.selectSegment(random, requiredSize, alignment, addressWidthBytes);
	}

	public RegisterDescriptor selectRegister(RegisterSet desiredResources, RandomGenerator random) throws NoPossibilitiesException {
		var intersection = allowedRegisters.intersection(desiredResources);

		if (intersection.isEmpty())
			throw new NoPossibilitiesException();

		return intersection.select(random);
	}

	public boolean canFulfil(int requiredSize, int alignment, int addressWidthBytes) {
		// roudn size up to the nearest power of 2
		if (requiredSize < 0)
			return false;

		requiredSize = Integer.highestOneBit(requiredSize - 1) << 1;
		alignment = Integer.highestOneBit(alignment - 1) << 1;

		return memory.canFulfil(requiredSize, alignment, addressWidthBytes);
	}
}
