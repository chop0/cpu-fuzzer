package ax.xz.fuzz.blocks;

import ax.xz.fuzz.runtime.ExecutableSequence;
import com.github.icedland.iced.x86.asm.CodeAssembler;

import java.util.*;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

public record InterleavedBlock(BlockPair parent, List<InterleavedEntry> items) implements Block {
	public InterleavedBlock {
		int leftIndex = -1;
		int rightIndex = -1;

		for (var entry : items) {
			int newIndex = entry.index();
			int currentIndex = switch (entry.side()) {
				case LEFT -> leftIndex;
				case RIGHT -> rightIndex;
			};

			if (!(newIndex > currentIndex))
				throw new IllegalArgumentException("Interleaved entries must be in increasing order");

			switch (entry.side()) {
				case LEFT -> leftIndex = newIndex;
				case RIGHT -> rightIndex = newIndex;
			}
		}
	}

	public int indexOf(BlockEntry entry) {
		if (entry instanceof InterleavedEntry e)
			return items.indexOf(e);
		else
			return -1;
	}

	public InterleavedBlock without(int entryIndex) {
		var newEntries = new ArrayList<InterleavedEntry>();

		newEntries.addAll(items.subList(0, entryIndex));
		newEntries.addAll(items.subList(entryIndex + 1, items.size()));

		return new InterleavedBlock(parent, newEntries);
	}

	public record InterleavedEntry(BlockPair parent, Side side, int index) implements BlockEntry {
		@Override
		public byte[] encode(long rip) {
			var block = switch (side) {
				case LEFT -> parent.lhs();
				case RIGHT -> parent.rhs();
			};

			return block.items().get(index).encode(rip);
		}

		public enum Side {
			LEFT, RIGHT
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof BlockEntry that)) return false;

			return Arrays.equals(encode(0), that.encode(0));
		}
	}
}
