package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.mutate.DeferredMutation;
import ax.xz.fuzz.mutate.MutationFactory;
import ax.xz.fuzz.runtime.CPUState;
import ax.xz.fuzz.runtime.ExecutionResult;
import ax.xz.fuzz.runtime.Tester;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.icedland.iced.x86.Code;
import com.github.icedland.iced.x86.FlowControl;
import com.github.icedland.iced.x86.Instruction;

import java.lang.foreign.MemorySegment;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.*;
import java.util.random.RandomGenerator;

import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.Code.*;

public class BlockGenerator {
	private static final int MAX_INSTRUCTIONS = System.getenv("MAX_INSTRUCTIONS") == null ? 10 : Integer.parseInt(System.getenv("MAX_INSTRUCTIONS"));
	private static final Opcode[] allOpcodes = OpcodeCache.loadOpcodes();

	public static int opcodeCount() {
		return allOpcodes.length;
	}



	private final Set<Opcode> disabled = new HashSet<>();
	private ResourcePartition resourcePartition;

	public BlockGenerator(ResourcePartition resourcePartition) {
		this.resourcePartition = resourcePartition;
	}

	public void setPartition(ResourcePartition partition) {
		resourcePartition = partition;
	}

	public Block createBasicBlock(RandomGenerator rng) throws BlockGenerator.NoPossibilitiesException {
		var mf = new MutationFactory();

		var entries = new ArrayList<Block.BlockEntry>();
		for (int i = 0; i < rng.nextInt(	1, MAX_INSTRUCTIONS); i++) {
			for (;;) {
				var variant = allOpcodes[rng.nextInt(allOpcodes.length)];
				if (disabled.contains(variant) || !variant.fulfilledBy(true, resourcePartition)) continue;

				var insn = variant.ofRandom(resourcePartition, rng);
				var mut = mf.createMutations(variant, insn, resourcePartition, rng);

				entries.add(new Block.BlockEntry(
						resourcePartition,
						variant,
						variant.ofRandom(resourcePartition, rng),
						List.of(mut)
				));
				break;
			}
		}

		return new BasicBlock(entries);
	}

	public static class NoPossibilitiesException extends Exception {

	}
}
