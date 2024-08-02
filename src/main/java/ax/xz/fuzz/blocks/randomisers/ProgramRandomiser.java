package ax.xz.fuzz.blocks.randomisers;

import ax.xz.fuzz.blocks.*;
import ax.xz.fuzz.instruction.*;
import ax.xz.fuzz.mutate.*;
import ax.xz.fuzz.runtime.Branch;
import ax.xz.fuzz.runtime.state.CPUState;
import ax.xz.fuzz.runtime.Config;
import ax.xz.fuzz.runtime.ExecutableSequence;
import ax.xz.fuzz.runtime.state.GeneralPurposeRegisters;
import ax.xz.fuzz.tester.saved_state;
import com.github.icedland.iced.x86.Instruction;

import java.lang.foreign.Arena;
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
	private static Map<Opcode, Integer> opcodeIndices;
	private static final ReentrantLock lock = new ReentrantLock();

	private final Config config;
	private final Map.Entry<ResourcePartition, ResourcePartition>[] resourceSplitCandidates;
	private final Map<Map.Entry<ResourcePartition, ResourcePartition>, Integer> resourceSplitCandidatesIndices;
	private final ResourcePartition partition;

	public ProgramRandomiser(Config config, ResourcePartition partition) {
		this.config = config;
		this.partition = partition;
		this.resourceSplitCandidates = new Map.Entry[32];

		var indices = new HashMap<Map.Entry<ResourcePartition, ResourcePartition>, Integer>();
		var random = new SplittableRandom();
		for (int i = 0; i < resourceSplitCandidates.length; i++) {
			resourceSplitCandidates[i] = selectResourceSplit0(random, partition);
			indices.put(resourceSplitCandidates[i], i);
		}

		this.resourceSplitCandidatesIndices = Collections.unmodifiableMap(indices);
	}

	public InvarianceTestCase selectTestCase(RandomGenerator rng) {
		var pairs = selectBlockPairs(rng, config.blockCount());
		var branches = selectBranches(rng, config.blockCount());

		var a = selectSequence(rng, pairs, branches);
		var b = selectSequence(rng, pairs, branches);

		return new InvarianceTestCase(pairs, branches, a, b, selectCPUState(rng, pairs[0].lhsPartition(), pairs[0].rhsPartition()));
	}

	public void reverseTestCase(ReverseRandomGenerator rng, InvarianceTestCase outcome) {
		reverseBlockPairs(rng, outcome.pairs());
		reverseBranches(rng, outcome.branches());

		reverseSequence(rng, outcome.pairs(), outcome.branches(), outcome.a());
		reverseSequence(rng, outcome.pairs(), outcome.branches(), outcome.b());

		reverseCPUState(rng, outcome.initialState());
	}

	public CPUState selectCPUState(RandomGenerator rng, ResourcePartition a, ResourcePartition b) {
		long[] gprs = new long[constituents.length];
		for (int i = 0; i < gprs.length; i++) {
			var rp = a.allowedRegisters().hasRegister(constituents[i]) ? a : b;
			gprs[i] = selectInterestingValue(rng, rp);
		}

		var data = MemorySegment.ofArray(new long[(int) saved_state.sizeof() / 8]);
		data.spliterator(JAVA_LONG).forEachRemaining(n -> n.set(JAVA_LONG, 0, rng.nextLong()));
		var state = CPUState.ofSavedState(data);
		return new CPUState(
			new GeneralPurposeRegisters(gprs).withRsp(partition.stack().address() + partition.stack().address()/2),
			state.zmm(),
			state.mmx(),
			state.rflags() & ~((1 << 8) | (1 << 18)) // mask off TF and AC
		);
	}

	public void reverseCPUState(ReverseRandomGenerator rng, CPUState outcome) {
		var data = MemorySegment.ofArray(new long[(int) saved_state.sizeof() / 8]);
		outcome.toSavedState(data);
		data.spliterator(JAVA_LONG).forEachRemaining(n -> reverseInterestingValue(rng, n.get(JAVA_LONG, 0)));
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

	public void reverseInterestingValue(ReverseRandomGenerator rng, long outcome) {
		boolean useMemory = partition.memory().contains(outcome, MEMORY_GRANULARITY);
		rng.pushBoolean(useMemory);

		if (useMemory)
			try {
				partition.reverseAddress(rng, MEMORY_GRANULARITY, MEMORY_GRANULARITY, 8, outcome);
				return;
			} catch (NoPossibilitiesException e) {
			}

		rng.pushLong(outcome);
	}

	public ExecutableSequence selectSequence(RandomGenerator rng, BlockPair[] pairs, Branch[] branches) {
		var blocks = new InterleavedBlock[branches.length];
		for (int i = 0; i < branches.length; i++) {
			blocks[i] = selectInterleaved(rng, pairs[i]);
		}

		return new ExecutableSequence(blocks, branches);
	}

	public void reverseSequence(ReverseRandomGenerator rng, BlockPair[] pairs, Branch[] branches, ExecutableSequence outcome) {
		for (int i = 0; i < branches.length; i++) {
			reverseInterleaved(rng, pairs[i], (InterleavedBlock) outcome.blocks()[i]);
		}
	}

	public InterleavedBlock selectInterleaved(RandomGenerator rng, BlockPair pair) {
		var lhs = pair.lhs();
		var rhs = pair.rhs();

		var picks = new BitSet(lhs.size() + rhs.size());

		{
			int lhsIndex = 0, rhsIndex = 0;
			for (int i = 0; i < (lhs.size() + rhs.size()); i++) {
				if ((rng.nextBoolean() && lhsIndex < lhs.size()) || rhsIndex >= rhs.size()) {
					picks.set(i, true);
					lhsIndex++;
				} else {
					rhsIndex++;
					picks.set(i, false);
				}
			}
		}

		return new InterleavedBlock(lhs, rhs, picks);
	}

	public void reverseInterleaved(ReverseRandomGenerator rng, BlockPair pair, InterleavedBlock outcome) {
		var lhs = pair.lhs();
		var rhs = pair.rhs();
		var picks = outcome.picks();

		for (int i = 0; i < lhs.size() + rhs.size(); i++) {
			rng.pushBoolean(picks.get(i));
		}
	}

	public BlockPair[] selectBlockPairs(RandomGenerator rng, int blockCount) {
		var pairs = new BlockPair[blockCount];
		for (int i = 0; i < blockCount; i++) {
			pairs[i] = selectBlockPair(rng);
		}
		return pairs;
	}

	public void reverseBlockPairs(ReverseRandomGenerator rng, BlockPair[] outcomes) {
		for (var outcome : outcomes) {
			reverseBlockPair(rng, outcome);
		}
	}

	public Branch[] selectBranches(RandomGenerator rng, int blockCount) {
		var branches = new Branch[blockCount];
		for (int i = 0; i < blockCount; i++) {
			branches[i] = selectBranch(rng, blockCount);
		}
		return branches;
	}

	public void reverseBranches(ReverseRandomGenerator rng, Branch[] outcomes) {
		for (var outcome : outcomes) {
			reverseBranch(rng, outcome);
		}
	}

	public Branch selectBranch(RandomGenerator rng, int blockCount) {
		var type = ExecutableSequence.BranchType.values()[rng.nextInt(ExecutableSequence.BranchType.values().length)];
		int taken = rng.nextInt(blockCount + 1); // + 1 because the last block is the exit block
		int notTaken = rng.nextInt(blockCount + 1);

		return new Branch(type, taken, notTaken);
	}

	public void reverseBranch(ReverseRandomGenerator rng, Branch outcome) {
		rng.pushInt(outcome.type().ordinal());
		rng.pushInt(outcome.takenIndex());
		rng.pushInt(outcome.notTakenIndex());
	}

	public BlockPair selectBlockPair(RandomGenerator rng) {
		var resources = selectResourceSplit(rng);
		var lhs = selectBlock(rng, resources.getKey());
		var rhs = selectBlock(rng, resources.getValue());

		return new BlockPair(lhs, rhs, resources.getKey(), resources.getValue());
	}

	public void reverseBlockPair(ReverseRandomGenerator rng, BlockPair outcome) {
		reverseResourceSplit(rng, outcome.lhsPartition(), outcome.rhsPartition());
		reverseBlock(rng, outcome.lhsPartition(), outcome.lhs());
		reverseBlock(rng, outcome.rhsPartition(), outcome.rhs());
	}

	public Map.Entry<ResourcePartition, ResourcePartition> selectResourceSplit(RandomGenerator rng) {
		return resourceSplitCandidates[rng.nextInt(resourceSplitCandidates.length)];

	}

	private Map.Entry<ResourcePartition, ResourcePartition> selectResourceSplit0(RandomGenerator rng, ResourcePartition partition) {
		var lhsRegisters = RegisterSet.of();
		var rhsRegisters = RegisterSet.of();

		var universe = partition.allowedRegisters();
		for (var bank : RegisterSet.bankSets) {
			if (rng.nextBoolean())
				lhsRegisters = lhsRegisters.union(bank.intersection(universe));
			else
				rhsRegisters = rhsRegisters.union(bank.intersection(universe));
		}

		var lhsFlags = EnumSet.noneOf(StatusFlag.class);
		if (rng.nextBoolean())
			Collections.addAll(lhsFlags, StatusFlag.values());

		var memorySplit = selectMemorySplit(rng);

		return Map.entry(
			new ResourcePartition(lhsFlags, lhsRegisters, memorySplit.getKey(), lhsRegisters.hasRegister(RSP) ? partition.stack() : MemorySegment.NULL),
			new ResourcePartition(EnumSet.complementOf(lhsFlags), rhsRegisters, memorySplit.getValue(), rhsRegisters.hasRegister(RSP) ? partition.stack() : MemorySegment.NULL)
		);
	}

	public void reverseResourceSplit(ReverseRandomGenerator rng, ResourcePartition lhs, ResourcePartition rhs) {
		rng.pushInt(resourceSplitCandidatesIndices.get(Map.entry(lhs, rhs)));
	}

	public Map.Entry<MemoryPartition, MemoryPartition> selectMemorySplit(RandomGenerator rng) {
		long totalSize = partition.memory().byteSize();

		var lhsPartition = new MemoryPartition(partition.memory().ms().asSlice(0, totalSize / 2));
		var rhsPartition = new MemoryPartition(partition.memory().ms().asSlice(totalSize / 2, totalSize / 2));

		return Map.entry(lhsPartition, rhsPartition);
	}

	public void reverseMemorySplit(ReverseRandomGenerator rng, MemoryPartition lhs, MemoryPartition rhs) {

	}

	public BasicBlock selectBlock(RandomGenerator rng, ResourcePartition partition) {
		var entries = new ArrayList<BlockEntry>();
		int instructionCount = rng.nextInt(config.maxInstructionCount());

		for (int i = 0; i < instructionCount; i++) {
			entries.add(selectBlockEntry(rng, partition));
		}

		return new BasicBlock(entries);
	}

	public void reverseBlock(ReverseRandomGenerator rng, ResourcePartition partition, BasicBlock outcome) {
		rng.pushInt(outcome.items().size());
		for (var entry : outcome.items()) {
			reverseBlockEntry(rng, partition, (BlockEntry.FuzzEntry) entry);
		}
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

	public void reverseBlockEntry(ReverseRandomGenerator rng, ResourcePartition partition, BlockEntry.FuzzEntry outcome) {
		var variant = outcome.opcode();

		rng.pushInt(opcodeIndices().get(variant));

		Instruction instruction = outcome.instruction();
		try {
			variant.reverse(rng, partition, instruction);
		} catch (NoPossibilitiesException e) {
			throw new RuntimeException(e);
		}

		reverseMutations(rng, partition, variant, instruction, outcome.mutations().toArray(new DeferredMutation[0]));
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

	public void reverseMutations(ReverseRandomGenerator rng, ResourcePartition partition, Opcode opcode, Instruction original, DeferredMutation[] mutations) {
		int i = 0;

		for (Mutator mutator : mutators) {
			if (i >= mutations.length)
				break;
			var item = mutations[i];
			if (mutator.comesFrom(partition, opcode, original, item)) {
				mutator.reverse(rng, partition, original, item);
				i++;
			}
		}

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

	private static Map<Opcode, Integer> opcodeIndices() {
		if (opcodeIndices == null) {
			lock.lock();
			try {
				if (opcodeIndices == null) {
					var indices = new HashMap<Opcode, Integer>();
					for (int i = 0; i < allOpcodes().length; i++) {
						indices.put(allOpcodes()[i], i);
					}

					opcodeIndices = Collections.unmodifiableMap(indices);
				}
			} finally {
				lock.unlock();
			}
		}
		return opcodeIndices;
	}


	public static void main(String[] args) {
		System.out.println("hello");
		var rp = new ResourcePartition(StatusFlag.all(), RegisterSet.ALL_AVX512, MemoryPartition.of(Arena.ofAuto().allocate(4096)), Arena.ofAuto().allocate(4096));
		var r = new ProgramRandomiser(Config.defaultConfig(), rp);

		var tc = r.selectTestCase(new SplittableRandom());
		System.out.println(tc);

		var rng = ReverseRandomGenerator.create();
		r.reverseTestCase(rng, tc);
		var tc2 = r.selectTestCase(rng.flip());
		System.out.println(tc2);
	}
}
