package ax.xz.fuzz.blocks.randomisers;

import ax.xz.fuzz.blocks.*;
import ax.xz.fuzz.instruction.*;
import ax.xz.fuzz.mutate.*;
import ax.xz.fuzz.runtime.Branch;
import ax.xz.fuzz.runtime.CPUState;
import ax.xz.fuzz.runtime.ExecutableSequence;
import ax.xz.fuzz.tester.saved_state;
import com.github.icedland.iced.x86.Instruction;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.random.RandomGenerator;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

public class ProgramRandomiser {
	public static final int MAX_INSTRUCTIONS = System.getenv("MAX_INSTRUCTIONS") == null ? 10 : Integer.parseInt(System.getenv("MAX_INSTRUCTIONS"));
	public static final int TRIALS = System.getenv("TRIALS") == null ? 2 : Integer.parseInt(System.getenv("TRIALS"));
	public static final int NUM_BLOCKS = System.getenv("NUM_BLOCKS") == null ? 5 : Integer.parseInt(System.getenv("NUM_BLOCKS"));

	public static final int MEMORY_GRANULARITY = 256;

	public static final Mutator[] mutators = {
		new PrefixAdder(),
		new RexAdder(),
		new VexAdder(),
		new PrefixDuplicator()
	};

	private static Opcode[] allOpcodes;
	private static Map<Opcode, Integer> opcodeIndices;

	private static final ReentrantLock lock = new ReentrantLock();

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

	public InvarianceTestCase selectTestCase(RandomGenerator rng, ResourcePartition partition) {
		var pairs = selectBlockPairs(rng, partition, NUM_BLOCKS);
		var branches = selectBranches(rng, NUM_BLOCKS);

		var sequences = new ExecutableSequence[TRIALS];
		for (int i = 0; i < TRIALS; i++) {
			sequences[i] = selectSequence(rng, pairs, branches);
		}

		return new InvarianceTestCase(pairs, branches, sequences, selectCPUState(rng, partition));
	}

	public void reverseTestCase(ReverseRandomGenerator rng, ResourcePartition partition, InvarianceTestCase outcome) {
		reverseBlockPairs(rng, outcome.pairs());
		reverseBranches(rng, outcome.branches());

		for (var sequence : outcome.sequences()) {
			reverseSequence(rng, outcome.pairs(), outcome.branches(), sequence);
		}

		reverseCPUState(rng, partition, outcome.initialState());
	}

	public CPUState selectCPUState(RandomGenerator rng, ResourcePartition partition) {
		var data = MemorySegment.ofArray(new long[(int) saved_state.sizeof()/8]);
		data.spliterator(JAVA_LONG).forEachRemaining(n -> n.set(JAVA_LONG, 0, selectInterestingValue(rng, partition)));
		return CPUState.ofSavedState(data);
	}

	public void reverseCPUState(ReverseRandomGenerator rng, ResourcePartition partition, CPUState outcome) {
		var data = MemorySegment.ofArray(new long[(int) saved_state.sizeof()/8]);
		outcome.toSavedState(data);
		data.spliterator(JAVA_LONG).forEachRemaining(n -> reverseInterestingValue(rng, partition, n.get(JAVA_LONG, 0)));
	}

	public long selectInterestingValue(RandomGenerator rng, ResourcePartition partition) {
		boolean useMemory = rng.nextBoolean();

		if (useMemory && !partition.memory().isEmpty())
			try {
				return partition.selectAddress(rng, MEMORY_GRANULARITY, MEMORY_GRANULARITY, 8);
			} catch (NoPossibilitiesException e) {
			}

		return rng.nextLong();
	}

	public void reverseInterestingValue(ReverseRandomGenerator rng, ResourcePartition partition, long outcome) {
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

	public BlockPair[] selectBlockPairs(RandomGenerator rng, ResourcePartition partition, int blockCount) {
		var pairs = new BlockPair[blockCount];
		for (int i = 0; i < blockCount; i++) {
			pairs[i] = selectBlockPair(rng, partition);
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

	public BlockPair selectBlockPair(RandomGenerator rng, ResourcePartition partition) {
		var resources = selectResourceSplit(rng, partition);
		var lhs = selectBlock(rng, resources.getKey());
		var rhs = selectBlock(rng, resources.getValue());

		return new BlockPair(lhs, rhs, resources.getKey(), resources.getValue());
	}

	public void reverseBlockPair(ReverseRandomGenerator rng, BlockPair outcome) {
		reverseResourceSplit(rng, outcome.lhsPartition(), outcome.rhsPartition());
		reverseBlock(rng, outcome.lhsPartition(), outcome.lhs());
		reverseBlock(rng, outcome.rhsPartition(), outcome.rhs());
	}

	public Map.Entry<ResourcePartition, ResourcePartition> selectResourceSplit(RandomGenerator rng, ResourcePartition partition) {
		var lhsRegisters = new BitSet();
		var rhsRegisters = new BitSet();

		var universe = partition.allowedRegisters();
		for (var bank : RegisterSet.bankSets) {
			var dst = rng.nextBoolean() ? lhsRegisters : rhsRegisters;
			for (int n : bank.intersection(universe)) {
				dst.set(n);
			}
		}

		var lhsFlags = EnumSet.noneOf(StatusFlag.class);
		if (rng.nextBoolean())
			Collections.addAll(lhsFlags, StatusFlag.values());

		var memorySplit = selectMemorySplit(rng, partition.memory());

		return Map.entry(
			new ResourcePartition(lhsFlags, new RegisterSet(lhsRegisters), memorySplit.getKey()),
			new ResourcePartition(EnumSet.complementOf(lhsFlags), new RegisterSet(rhsRegisters), memorySplit.getValue())
		);
	}

	public void reverseResourceSplit(ReverseRandomGenerator rng, ResourcePartition lhs, ResourcePartition rhs) {
		for (var bank : RegisterSet.bankSets) {
			rng.pushBoolean( lhs.allowedRegisters().intersects(bank));
		}

		rng.pushBoolean(lhs.statusFlags().containsAll(rhs.statusFlags()));
		reverseMemorySplit(rng, lhs.memory(), rhs.memory());
	}

	public Map.Entry<MemoryPartition, MemoryPartition> selectMemorySplit(RandomGenerator rng, MemoryPartition partition) {
		long totalSize = partition.byteSize();
		long segmentCountEst = totalSize / MEMORY_GRANULARITY;

		var lhsSegments = new ArrayList<MemorySegment>((int) (segmentCountEst / 2));
		var rhsSegments = new ArrayList<MemorySegment>((int) (segmentCountEst / 2));

		partition.segments(MEMORY_GRANULARITY, MEMORY_GRANULARITY, 8)
			.forEachOrdered(segment -> {
				if (rng.nextBoolean()) {
					lhsSegments.add(segment);
				} else {
					rhsSegments.add(segment);
				}
			});

		var lhsPartition = new MemoryPartition(lhsSegments.toArray(MemorySegment[]::new));
		var rhsPartition = new MemoryPartition(rhsSegments.toArray(MemorySegment[]::new));

		return Map.entry(lhsPartition, rhsPartition);
	}

	public void reverseMemorySplit(ReverseRandomGenerator rng, MemoryPartition lhs, MemoryPartition rhs) {
		var union = lhs.union(rhs);

		union.segments(MEMORY_GRANULARITY, MEMORY_GRANULARITY, 8)
			.forEachOrdered(segment -> {
				rng.pushBoolean(lhs.contains(segment.address(), segment.byteSize()));
			});
	}

	public BasicBlock selectBlock(RandomGenerator rng, ResourcePartition partition) {
		var entries = new ArrayList<Block.BlockEntry>();
		int instructionCount = rng.nextInt(MAX_INSTRUCTIONS);

		for (int i = 0; i < instructionCount; i++) {
			entries.add(selectBlockEntry(rng, partition));
		}

		return new BasicBlock(entries);
	}

	public void reverseBlock(ReverseRandomGenerator rng, ResourcePartition partition, BasicBlock outcome) {
		rng.pushInt(outcome.items().size());
		for (var entry : outcome.items()) {
			reverseBlockEntry(rng, partition, entry);
		}
	}

	public Block.BlockEntry selectBlockEntry(RandomGenerator rng, ResourcePartition partition) {
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

			return new Block.BlockEntry(
				partition,
				variant,
				instruction,
				List.of(mut)
			);
		}
	}

	public void reverseBlockEntry(ReverseRandomGenerator rng, ResourcePartition partition, Block.BlockEntry outcome) {
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

	public static void main(String[] args) {
		System.out.println("hello");
		var r = new ProgramRandomiser();
		var rp = ResourcePartition.all(true, Arena.ofAuto().allocate(4096));

		var tc = r.selectTestCase(new SplittableRandom(), rp);
		System.out.println(tc);

		var rng = ReverseRandomGenerator.create();
		r.reverseTestCase(rng, rp, tc);
		var tc2 = r.selectTestCase(rng.flip(), rp);
		System.out.println(tc2);
	}
}
