package ax.xz.fuzz.runtime;

import ax.xz.fuzz.arch.Branch;
import ax.xz.fuzz.blocks.Block;

import java.util.Arrays;
import java.util.Objects;

public final class ExecutableSequence {
	private final Block[] blocks;
	private final Branch[] branches;

	public ExecutableSequence(Block[] blocks, Branch[] branches) {
		if (blocks.length == 0)
			throw new IllegalArgumentException("blocks must not be empty");
		this.blocks = new Block[blocks.length];
		this.branches = new Branch[branches.length];

		System.arraycopy(blocks, 0, this.blocks, 0, blocks.length);
		System.arraycopy(branches, 0, this.branches, 0, branches.length);
	}

	public Block[] blocks() {
		return blocks;
	}

	public Branch[] branches() {
		return branches;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (ExecutableSequence) obj;
		return Arrays.equals(this.blocks, that.blocks) &&
		       Arrays.equals(this.branches, that.branches);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(blocks), Arrays.hashCode(branches));
	}

}
