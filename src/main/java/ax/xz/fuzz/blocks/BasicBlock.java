package ax.xz.fuzz.blocks;

import java.util.*;


public record BasicBlock(List<BlockEntry> items) implements Block {
	public static BasicBlock ofEncoded(byte[][] blocks) {
		var items = new ArrayList<BlockEntry>();
		for (var block : blocks) {
			items.add(new BlockEntry.ConcreteEntry(block));
		}
		return new BasicBlock(items);
	}

	@Override
	public int size() {
		return items.size();
	}

	@Override
	public Block without(int index) {
		var newItems = new ArrayList<>(items);
		newItems.remove(index);
		return new BasicBlock(newItems);
	}

	@Override
	public int indexOf(BlockEntry entry) {
		return items.indexOf(entry);
	}
}
