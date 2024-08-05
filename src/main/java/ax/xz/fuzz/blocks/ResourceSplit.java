package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.ResourcePartition;

public record ResourceSplit(ResourcePartition left, ResourcePartition right, ResourcePartition master) {
}
