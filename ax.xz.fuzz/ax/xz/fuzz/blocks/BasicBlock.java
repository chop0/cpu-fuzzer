package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.runtime.TestCase;
import ax.xz.fuzz.mutate.DeferredMutation;
import com.github.icedland.iced.x86.Instruction;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.random.RandomGenerator;


public class BasicBlock implements Block {

	private final List<BlockEntry> items;

	public BasicBlock(List<BlockEntry> items) {
		this.items = List.copyOf(items);
	}

	@Override
	public SequencedCollection<BlockEntry> items() {
		return items;
	}

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
