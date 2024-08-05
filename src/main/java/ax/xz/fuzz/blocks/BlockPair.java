package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.ResourcePartition;

public record BlockPair(BasicBlock lhs, BasicBlock rhs, ResourceSplit split) {

}
