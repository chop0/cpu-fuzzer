package ax.xz.fuzz.instruction;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.blocks.randomisers.ReverseRandomGenerator;

import java.lang.foreign.MemorySegment;
import java.util.*;
import java.util.random.RandomGenerator;

public record ResourcePartition(EnumSet<StatusFlag> statusFlags, RegisterSet allowedRegisters, MemoryPartition memory) {
	public ResourcePartition {
		if (statusFlags == null)
			statusFlags = EnumSet.noneOf(StatusFlag.class);
		if (allowedRegisters == null)
			allowedRegisters = RegisterSet.of();
		if (memory == null)
			memory = MemoryPartition.empty();
	}

	public static ResourcePartition all(boolean evex) {
		return new ResourcePartition(StatusFlag.all(), evex ? RegisterSet.ALL_EVEX : RegisterSet.ALL_VEX, MemoryPartition.addressSpace64());
	}

	public static ResourcePartition all(boolean evex, MemorySegment ms) {
		return new ResourcePartition(StatusFlag.all(), evex ? RegisterSet.ALL_EVEX : RegisterSet.ALL_VEX, MemoryPartition.of(ms));
	}


	public long selectAddress(RandomGenerator random, int requiredSize, int alignment, int addressWidthBytes) throws NoPossibilitiesException {
		return memory.selectSegment(random, requiredSize, alignment, addressWidthBytes);
	}

	public void reverseAddress(ReverseRandomGenerator random, int requiredSize, int alignment, int addressWidthBytes, long outcome) throws NoPossibilitiesException {
		memory.reverseSegment(random, requiredSize, alignment, addressWidthBytes, outcome);
	}

	public int selectRegister(RegisterSet desiredResources, RandomGenerator random) throws NoPossibilitiesException {
		var intersection = allowedRegisters.intersection(desiredResources);

		if (intersection.isEmpty())
			throw new NoPossibilitiesException();

		return intersection.select(random);
	}

	public void reverseRegister(RegisterSet desiredResources, ReverseRandomGenerator random, int outcome) throws NoPossibilitiesException {
		var intersection = allowedRegisters.intersection(desiredResources);

		if (intersection.isEmpty())
			throw new NoPossibilitiesException();

		intersection.reverse(random, outcome);
	}

	public boolean canFulfil(int requiredSize, int alignment, int addressWidthBytes) {
		// roudn size up to the nearest power of 2
		requiredSize = Integer.highestOneBit(requiredSize - 1) << 1;
		alignment = Integer.highestOneBit(alignment - 1) << 1;

		return memory.canFulfil(requiredSize, alignment, addressWidthBytes);
	}

	public ResourcePartition withAllowedRegisters(RegisterSet allowedRegisters) {
		return new ResourcePartition(statusFlags, allowedRegisters, memory);
	}

	public ResourcePartition withMemory(MemoryPartition memory) {
		return new ResourcePartition(statusFlags, allowedRegisters, memory);
	}
}
