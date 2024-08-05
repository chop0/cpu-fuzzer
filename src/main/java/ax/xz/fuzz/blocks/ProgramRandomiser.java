package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.*;
import ax.xz.fuzz.mutate.*;
import ax.xz.fuzz.runtime.Branch;
import ax.xz.fuzz.runtime.Config;
import ax.xz.fuzz.runtime.ExecutableSequence;
import ax.xz.fuzz.runtime.state.CPUState;
import ax.xz.fuzz.runtime.state.GeneralPurposeRegisters;
import ax.xz.fuzz.tester.saved_state;
import com.github.icedland.iced.x86.Instruction;

import java.lang.foreign.MemorySegment;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.random.RandomGenerator;

import static ax.xz.fuzz.runtime.state.GeneralPurposeRegisters.constituents;
import static com.github.icedland.iced.x86.Register.RSP;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

public class ProgramRandomiser {
	public static final int MEMORY_GRANULARITY = 64;

	public static final Mutator[] mutators = {
		new PrefixAdder(),
		new RexAdder(),  // TODO : fix vex adder
//		new VexAdder(),
		new PrefixDuplicator()
	};

	private static Opcode[] allOpcodes;
	private static final ReentrantLock lock = new ReentrantLock();

	private final Config config;

	public ProgramRandomiser(Config config) {
		this.config = config;
	}

	public InvarianceTestCase selectTestCase(RandomGenerator rng, ResourcePartition master) {
		var split = selectResourceSplit(rng, master);

		var pairs = selectBlockPairs(rng, split);
		var branches = selectBranches(rng);

		var a = selectSequence(rng, pairs, branches);
		var b = selectSequence(rng, pairs, branches);

		return new InvarianceTestCase(branches, a, b, selectCPUState(rng, split));
	}

	public CPUState selectCPUState(RandomGenerator rng, ResourceSplit split) {
		var a = split.left();
		var  b = split.right();
		var master = split.master();

		long[] gprs = new long[constituents.length];
		for (int i = 0; i < gprs.length; i++) {
			var rp = a.allowedRegisters().hasRegister(constituents[i]) ? a : b;
			gprs[i] = selectInterestingValue(rng, rp);
		}

		var data = MemorySegment.ofArray(new long[(int) saved_state.sizeof() / 8]);
		data.spliterator(JAVA_LONG).forEachRemaining(n -> n.set(JAVA_LONG, 0, rng.nextLong()));
		var state = CPUState.ofSavedState(data);
		return new CPUState(
			new GeneralPurposeRegisters(gprs).withRsp(master.stack().address() + master.stack().byteSize()/2),
			state.zmm(),
			state.mmx(),
			state.rflags() & ~((1 << 8) | (1 << 18)) // mask off TF and AC
		);
	}

	public long selectInterestingValue(RandomGenerator rng, ResourcePartition rp) {
		boolean useMemory = rng.nextBoolean();

		if (useMemory && !rp.memory().isEmpty())
			try {
				return rp.selectAddress(rng, MEMORY_GRANULARITY, MEMORY_GRANULARITY, 8);
			} catch (NoPossibilitiesException e) {
			}

		return rng.nextLong();
	}

	public ExecutableSequence selectSequence(RandomGenerator rng, BlockPair[] pairs, Branch[] branches) {
		var blocks = new InterleavedBlock[branches.length];
		for (int i = 0; i < branches.length; i++) {
			blocks[i] = selectInterleaved(rng, pairs[i]);
		}

		return new ExecutableSequence(blocks, branches);
	}

	public InterleavedBlock selectInterleaved(RandomGenerator rng, BlockPair pair) {
		var lhs = pair.lhs();
		var rhs = pair.rhs();

		var entries = new ArrayList<InterleavedBlock.InterleavedEntry>();
		int leftIndex = 0;
		int rightIndex = 0;

		for (int i = 0; i < lhs.size() + rhs.size(); i++) {
			if (leftIndex < lhs.size() && (rightIndex == rhs.size() || rng.nextBoolean())) {
				entries.add(new InterleavedBlock.InterleavedEntry(pair, InterleavedBlock.InterleavedEntry.Side.LEFT, leftIndex));
				leftIndex++;
			} else {
				entries.add(new InterleavedBlock.InterleavedEntry(pair, InterleavedBlock.InterleavedEntry.Side.RIGHT, rightIndex));
				rightIndex++;
			}
		}

		return new InterleavedBlock(pair, entries);
	}

	public BlockPair[] selectBlockPairs(RandomGenerator rng, ResourceSplit split) {
		var pairs = new BlockPair[config.blockCount()];
		for (int i = 0; i < pairs.length; i++) {
			pairs[i] = selectBlockPair(rng, split);
		}
		return pairs;
	}

	public Branch[] selectBranches(RandomGenerator rng) {
		var branches = new Branch[config.blockCount()];
		for (int i = 0; i < config.blockCount(); i++) {
			branches[i] = selectBranch(rng);
		}
		return branches;
	}

	public Branch selectBranch(RandomGenerator rng) {
		var type = ExecutableSequence.BranchType.values()[rng.nextInt(ExecutableSequence.BranchType.values().length)];
		int taken = rng.nextInt(config.blockCount() + 1); // + 1 because the last block is the exit block
		int notTaken = rng.nextInt(config.blockCount() + 1);

		return new Branch(type, taken, notTaken);
	}

	public BlockPair selectBlockPair(RandomGenerator rng, ResourceSplit split) {
		var lhs = selectBlock(rng, split.left());
		var rhs = selectBlock(rng, split.right());

		return new BlockPair(lhs, rhs, split);
	}


	private ResourceSplit selectResourceSplit(RandomGenerator rng, ResourcePartition master) {
		var lhsRegisters = RegisterSet.of();
		var rhsRegisters = RegisterSet.of();

		var universe = master.allowedRegisters();
		for (var bank : RegisterSet.bankSets) {
			if (rng.nextBoolean())
				lhsRegisters = lhsRegisters.union(bank.intersection(universe));
			else
				rhsRegisters = rhsRegisters.union(bank.intersection(universe));
		}

		var lhsFlags = EnumSet.noneOf(StatusFlag.class);
		if (rng.nextBoolean())
			Collections.addAll(lhsFlags, StatusFlag.values());

		var memorySplit = selectMemorySplit(master);

		return new ResourceSplit(
			new ResourcePartition(lhsFlags, lhsRegisters, memorySplit.getKey(), lhsRegisters.hasRegister(RSP) ? master.stack() : MemorySegment.NULL),
			new ResourcePartition(EnumSet.complementOf(lhsFlags), rhsRegisters, memorySplit.getValue(), rhsRegisters.hasRegister(RSP) ? master.stack() : MemorySegment.NULL),
			master
		);
	}

	public Map.Entry<MemoryPartition, MemoryPartition> selectMemorySplit(ResourcePartition master) {
		long totalSize = master.memory().byteSize();

		var lhsPartition = new MemoryPartition(master.memory().ms().asSlice(0, totalSize / 2));
		var rhsPartition = new MemoryPartition(master.memory().ms().asSlice(totalSize / 2, totalSize / 2));

		return Map.entry(lhsPartition, rhsPartition);
	}

	public BasicBlock selectBlock(RandomGenerator rng, ResourcePartition partition) {
		var entries = new ArrayList<BlockEntry>();
		int instructionCount = rng.nextInt(config.maxInstructionCount());

		for (int i = 0; i < instructionCount; i++) {
			entries.add(selectBlockEntry(rng, partition));
		}

		return new BasicBlock(entries);
	}

	public BlockEntry.FuzzEntry selectBlockEntry(RandomGenerator rng, ResourcePartition partition) {
		for (; ; ) {
			var variant = allOpcodes()[rng.nextInt(allOpcodes().length)];
			if (!variant.fulfilledBy(true, partition)) continue;

			Instruction instruction = null;
			try {
				instruction = variant.select(rng, partition);
			} catch (NoPossibilitiesException e) {
				throw new RuntimeException(e);
			}

			var mut = selectMutations(rng, partition, variant, instruction);

			return new BlockEntry.FuzzEntry(
				partition,
				variant,
				instruction,
				List.of(mut)
			);
		}
	}

	public DeferredMutation[] selectMutations(RandomGenerator rng, ResourcePartition partition, Opcode opcode, Instruction original) {
		var mutations = new ArrayList<DeferredMutation>();

		for (Mutator mutator : mutators) {
			if (mutator.appliesTo(partition, opcode, original)) {
				mutations.add(mutator.select(rng, partition, original));
			}
		}

		return mutations.toArray(new DeferredMutation[0]);
	}

	private static Opcode[] allOpcodes() {
		if (allOpcodes == null) {
			lock.lock();
			try {
				if (allOpcodes == null) {
					allOpcodes = OpcodeCache.loadOpcodes();
				}
			} finally {
				lock.unlock();
			}
		}

		return allOpcodes;
	}
}
