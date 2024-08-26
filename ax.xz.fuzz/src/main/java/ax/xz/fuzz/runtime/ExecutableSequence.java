package ax.xz.fuzz.runtime;

import ax.xz.fuzz.arch.BlockEdge;
import ax.xz.fuzz.blocks.Block;

import java.util.Arrays;
import java.util.Objects;

public final class ExecutableSequence {
	private final Block[] blocks;
	private final BlockEdge[] blockEdges;

	public ExecutableSequence(Block[] blocks, BlockEdge[] blockEdges) {
		if (blocks.length == 0)
			throw new IllegalArgumentException("blocks must not be empty");
		this.blocks = new Block[blocks.length];
		this.blockEdges = new BlockEdge[blockEdges.length];

		System.arraycopy(blocks, 0, this.blocks, 0, blocks.length);
		System.arraycopy(blockEdges, 0, this.blockEdges, 0, blockEdges.length);
	}

	public Block[] blocks() {
		return blocks;
	}

	public BlockEdge[] branches() {
		return blockEdges;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (ExecutableSequence) obj;
		return Arrays.equals(this.blocks, that.blocks) &&
		       Arrays.equals(this.blockEdges, that.blockEdges);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(blocks), Arrays.hashCode(blockEdges));
	}

}
