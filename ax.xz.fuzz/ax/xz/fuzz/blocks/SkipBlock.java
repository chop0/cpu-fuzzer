package ax.xz.fuzz.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SequencedCollection;

public class SkipBlock implements Block {
	private final Block delegate;
	private final List<BlockEntry> list;
	private final List<Integer> skips;

	public SkipBlock(Block delegate, List<Integer> skips) {
		this.delegate = delegate;
		this.skips = skips;

		if (skips.stream().anyMatch(i -> i < 0 || i >= delegate.size())) {
			throw new IllegalArgumentException("Invalid skip index");
		}

		this.list = new ArrayList<>();

		int i = 0;
		for (var item : delegate.items()) {
			if (!skips.contains(i++)) {
				list.add(item);
			}
		}

		assert list.size() == delegate.size() - skips.size();
	}

	@Override
	public int size() {
		return delegate.size() - skips.size();
	}

	@Override
	public SequencedCollection<BlockEntry> items() {
		return Collections.unmodifiableSequencedCollection(list);
	}

	@Override
	public Block without(int... instructionIndex) {
		var newSkips = new ArrayList<>(skips);
		for (int i : instructionIndex) {
			newSkips.add(i);
		}
		return new SkipBlock(delegate, newSkips);
	}
}
