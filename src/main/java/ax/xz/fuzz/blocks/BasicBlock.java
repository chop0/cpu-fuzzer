package ax.xz.fuzz.blocks;

import java.util.*;


public record BasicBlock(List<BlockEntry> items) implements Block {

	@Override
	public int size() {
		return items.size();
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();
		for (var item : items) {
			for (var deferredMutation : item.mutations()) {
				sb.append(deferredMutation).append(" ");
			}
			sb.append(item.instruction()).append("\n");
		}

		return sb.toString();
	}

	@Override
	public Block without(int... instructionIndex) {
		var newItems = new ArrayList<BlockEntry>();
		for (int i = 0; i < items.size(); i++) {
			int finalI = i;
			if (Arrays.stream(instructionIndex).noneMatch(j -> j == finalI)) {
				newItems.add(items.get(i));
			}
		}
		return new BasicBlock(newItems);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (Block) obj;
		return Objects.equals(this.items, that.items());
	}

	@Override
	public int hashCode() {
		return items.hashCode();
	}
}
