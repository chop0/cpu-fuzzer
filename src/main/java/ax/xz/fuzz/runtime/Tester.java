package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.Block;
import ax.xz.fuzz.blocks.InterleavedBlock;
import ax.xz.fuzz.blocks.InvarianceTestCase;
import ax.xz.fuzz.blocks.randomisers.ProgramRandomiser;
import ax.xz.fuzz.instruction.MemoryPartition;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.instruction.StatusFlag;
import ax.xz.fuzz.tester.execution_result;
import com.github.icedland.iced.x86.asm.CodeAssembler;

import java.io.PrintWriter;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.function.BiFunction;
import java.util.random.RandomGenerator;

import static ax.xz.fuzz.runtime.ExecutionResult.interestingMismatch;
import static ax.xz.fuzz.runtime.MemoryUtils.Protection.*;
import static ax.xz.fuzz.tester.slave_h.do_test;
import static ax.xz.fuzz.tester.slave_h.maybe_allocate_signal_stack;
import static com.github.icedland.iced.x86.Register.*;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class Tester {

	private static final RegisterSet BANNED_REGISTERS = RegisterSet.of(R15L, R15W, R15D, R15);

	public final Trampoline trampoline;

	private final MemorySegment code;

	private final ProgramRandomiser randomiser;
	final ResourcePartition masterPartition;

	private final RandomGenerator rng = new SplittableRandom();

	public Tester(Config config, Trampoline trampoline, MemorySegment code, ResourcePartition masterPartition) {
		this.trampoline = trampoline;
		this.code = code;
		this.masterPartition = masterPartition;
		this.randomiser = new ProgramRandomiser(config, masterPartition);
	}

	public static Tester create(Config config, RegisterSet registers, EnumSet<StatusFlag> flags) {
		class holder {
			private static final VarHandle indexHandle;

			static {
				try {
					indexHandle = MethodHandles.lookup().findStaticVarHandle(holder.class, "index", long.class);
				} catch (NoSuchFieldException | IllegalAccessException e) {
					throw new AssertionError(e);
				}
			}

			private static volatile long index = 0;

			private static long nextIndex() {
				return (long) indexHandle.getAndAdd(1);
			}
		}

		long index = holder.nextIndex();

		return withIndex(config, registers, flags, index);
	}

	public static Tester withIndex(Config config, RegisterSet registers, EnumSet<StatusFlag> flags, long index) {
		var arena = Arena.global();
		var scratch = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x1100000 + index * 8192L * 2), 8192, READ, WRITE);

		var code = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x1310000 + index * 4096L * 16 * 2), 4096 * 16, READ, WRITE, EXECUTE);
		var stack = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x6a30000 + index * 4096L * 16 * 2), 4096, READ, WRITE, EXECUTE);

		var trampoline = Trampoline.create(arena);

		var masterPartition = new ResourcePartition(flags, registers.subtract(BANNED_REGISTERS), MemoryPartition.of(scratch), stack);

		maybe_allocate_signal_stack();
		return new Tester(config, trampoline, code, masterPartition);
	}

	public static Tester forRecordedCase(Config config, RecordedTestCase rtc) {
		var arena = Arena.global();
		var scratch = MemoryUtils.mmap(arena, MemorySegment.ofAddress(rtc.scratchRegions()[0]), 8192, READ, WRITE);
		long index = (rtc.scratchRegions()[0] - 0x1100000) / (8192 * 2);

		var code = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x1310000 + index * 4096L * 16 * 2), 4096 * 16, READ, WRITE, EXECUTE);
		var stack = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x6a30000 + index * 4096L * 16 * 2), 4096, READ, WRITE, EXECUTE);

		var trampoline = Trampoline.create(arena, rtc.trampolineLocation());

		maybe_allocate_signal_stack();
		return new Tester(config, trampoline, code, new ResourcePartition(StatusFlag.all(), RegisterSet.ALL_EVEX, MemoryPartition.of(scratch), stack));
	}

	private static final Object printLock = new Object();

	private static void printBytesGrouped(byte[] bytes, PrintWriter writer) {
		for (int i = 0; i < bytes.length; i += 64) {
			for (int j = 0; j < 64 && i + j < bytes.length; j++) {
				writer.printf("%02x ", bytes[i + j] & 0xff);
			}
			writer.println();
		}
	}

	public Map.Entry<ExecutionResult[], Optional<RecordedTestCase>> runTest(PrintWriter writeTestCases, boolean alwaysRecord) {
		var tc = randomiser.selectTestCase(rng);
		RecordedTestCase recorded = null; // only make this if we find a mismatch

		var results = new ExecutionResult[tc.sequences().length];

		if (writeTestCases != null)
			writeTestCases.println("--- begin ---");

		ExecutionResult previousResult = null;
		ExecutableSequence previousBlock = null;
		byte[] previousCode = null;

		ExecutableSequence[] sequences = tc.sequences();
		for (int i = 0; i < sequences.length; i++) {
			var test = sequences[i];

			if (writeTestCases != null)
				writeTestCases.println("--- next sequence ---");

			SequenceResult output = runSequence(tc.initialState(), test, writeTestCases);

			if (writeTestCases != null)
				writeTestCases.println("--- ok sequence didnt die ---");


			if (interestingMismatch(previousResult, output.result())) {
				var minimised = minimise(tc);
				recorded = record(minimised);
			}

			if (alwaysRecord) {
				recorded = record(tc);
			}

			results[i] = previousResult = output.result();
		}

		return Map.entry(results, Optional.ofNullable(recorded));
	}

	private RecordedTestCase record(InvarianceTestCase tc) {
		var code1 = encodeRelevantInstructions(tc.sequences()[0]);
		var code2 = encodeRelevantInstructions(tc.sequences()[1]);

		return new RecordedTestCase(tc.initialState(), code1, code2, trampoline.address().address(), tc.branches(), new long[]{masterPartition.memory().ms().address(), masterPartition.stack().address()});
	}

	private byte[][][] encodeRelevantInstructions(ExecutableSequence seq) {
		var asm = new CodeAssembler(64);
		var result = new byte[seq.blocks().length][][];
		Block[] blocks = seq.blocks();
		for (int i = 0; i < blocks.length; i++) {
			var block = blocks[i];
			result[i] = new byte[block.size()][];
			asm.reset();

			int j = 0;
			for (var item : block.items()) {
				var bytes = ExecutableSequence.encode(asm, item.instruction());

				for (var deferredMutation : item.mutations()) {
					bytes = deferredMutation.perform(bytes);
				}

				result[i][j++] = bytes;
			}
		}

		return result;
	}

	private SequenceResult runSequence(CPUState initialState, ExecutableSequence test) {
		return runSequence(initialState, test, null);
	}

	private SequenceResult runSequence(CPUState initialState, ExecutableSequence test, PrintWriter writer) {

		int codeLength = test.encode(code.address(), trampoline, code, R15, 100);
		var codeSlice = code.asSlice(0, codeLength);

		if (writer != null) {
			printBytesGrouped(codeSlice.toArray(JAVA_BYTE), writer);
			writer.flush();
		}

		var result = runBlock(initialState, codeSlice);
		SequenceResult output = new SequenceResult(codeSlice, result);
		return output;
	}

	private record SequenceResult(MemorySegment codeSlice, ExecutionResult result) {
	}

	public InvarianceTestCase minimise(InvarianceTestCase tc) {
		if (tc.sequences().length != 2)
			throw new IllegalArgumentException("Can only minimise two-sequence test cases");

		var test1 = tc.sequences()[0].blocks();
		var test2 = tc.sequences()[1].blocks();
		var branches = tc.branches();
		var startState = tc.initialState();

		for (; ; ) {
			boolean changed = false;

			var simplifiedState = simplifyInitialState(startState, new ExecutableSequence(test1, branches), new ExecutableSequence(test2, branches));
			if (!simplifiedState.equals(startState)) {
				startState = simplifiedState;
				System.err.println("Simplified initial state");
				continue;
			}

			for (int block = 0; block < test1.length; block++) {
				if (!(test1[block] instanceof InterleavedBlock a && test2[block] instanceof InterleavedBlock b)) {
					System.out.println("Skipping non-interleaved block");
					continue;
				}

				var redundantLeft = findRedundantInstructions(a.lhs().size(), startState, test1, test2, branches, block, InterleavedBlock::leftInterleavedIndex);

				if (!redundantLeft.isEmpty()) {
					test1[block] = a.without(redundantLeft.stream().mapToInt(a::leftInterleavedIndex).toArray());
					test2[block] = b.without(redundantLeft.stream().mapToInt(b::leftInterleavedIndex).toArray());

					changed = true;
					System.err.printf("Removed %d redundant instructions from block %d%n", redundantLeft.size(), block);
					break;
				}

				var redundantRight = findRedundantInstructions(a.rhs().size(), startState, test1, test2, branches, block, InterleavedBlock::rightInterleavedIndex);

				if (!redundantRight.isEmpty()) {
					test1[block] = a.without(redundantRight.stream().mapToInt(a::rightInterleavedIndex).toArray());
					test2[block] = b.without(redundantRight.stream().mapToInt(b::rightInterleavedIndex).toArray());

					changed = true;
					System.err.printf("Removed %d redundant instructions from block %d%n", redundantRight.size(), block);
					break;
				}
			}

			if (!changed)
				break;
		}

		var sequences = new ExecutableSequence[]{new ExecutableSequence(test1, branches), new ExecutableSequence(test2, branches)};
		return new InvarianceTestCase(tc.pairs(), tc.branches(), sequences, startState);
	}

	private ArrayList<Integer> findRedundantInstructions(int victimBlockSize, CPUState startState, Block[] test1, Block[] test2, Branch[] branches, int blockToReduce, BiFunction<InterleavedBlock, Integer, Integer> getVictimIndex) {
		var a = (InterleavedBlock) test1[blockToReduce];
		var b = (InterleavedBlock) test2[blockToReduce];

		assert a.size() == b.size();

		var redundantIndices = new ArrayList<Integer>();
		for (int i = 0; i < victimBlockSize; i++) {
			int test1Index = getVictimIndex.apply(a, i);
			int test2Index = getVictimIndex.apply(b, i);

			var aWithout = a.without(test1Index);
			var bWithout = b.without(test2Index);

			var test1Changed = new Block[test1.length];
			var test2Changed = new Block[test2.length];

			System.arraycopy(test1, 0, test1Changed, 0, test1.length);
			System.arraycopy(test2, 0, test2Changed, 0, test2.length);

			test1Changed[blockToReduce] = aWithout;
			test2Changed[blockToReduce] = bWithout;

			var test1Result = runSequence(startState, new ExecutableSequence(test1Changed, branches), null);
			var test2Result = runSequence(startState, new ExecutableSequence(test2Changed, branches), null);

			if (interestingMismatch(test1Result.result, test2Result.result)) {
				redundantIndices.add(i);
			}
		}

		return redundantIndices;
	}

	private CPUState simplifyInitialState(CPUState state, ExecutableSequence... sequences) {
		for (int i = 0; i < 32; i++) {
			var zmm = state.zmm().withZeroed(i);
			if (zmm.equals(state.zmm()))
				continue;

			var newState = new CPUState(state.gprs(), zmm, state.mmx(), state.rflags());

			ExecutionResult previous = null;
			for (var sequence : sequences) {
				var result = runSequence(newState, sequence).result;
				if (interestingMismatch(previous, result)) {
					return newState;
				}
				previous = result;
			}
		}

		for (int i = 0; i < 8; i++) {
			var mmx = state.mmx().withZeroed(i);
			if (mmx.equals(state.mmx()))
				continue;

			var newState = new CPUState(state.gprs(), state.zmm(), mmx, state.rflags());

			boolean consistent = true;
			ExecutionResult previous = null;
			for (var sequence : sequences) {
				var result = runSequence(newState, sequence).result;
				if (interestingMismatch(previous, result)) {
					consistent = false;
					break;
				}
				previous = result;
			}

			if (!consistent) {
				return newState;
			}
		}

		for (int i = 0; i < 16; i++) {
			var gprs = state.gprs().withZeroed(i);
			if (gprs.equals(state.gprs()))
				continue;
			var newState = new CPUState(gprs, state.zmm(), state.mmx(), state.rflags());

			boolean consistent = true;
			ExecutionResult previous = null;
			for (var sequence : sequences) {
				var result = runSequence(newState, sequence).result;
				if (interestingMismatch(previous, result)) {
					consistent = false;
					break;
				}
				previous = result;
			}

			if (!consistent) {
				return newState;
			}
		}

		return state;
	}

	public static ExecutionResult runBlock(CPUState startState, Block block) throws Block.UnencodeableException {
		try (var arena = Arena.ofShared()) {
			var code = MemoryUtils.mmap(arena, MemorySegment.NULL, (block.size() + 1) * 15L, READ, WRITE, EXECUTE);

			var trampoline = Trampoline.create(arena);

			int[] locations = block.encode(trampoline, code);

			var output = execution_result.allocate(arena);
			startState.toSavedState(execution_result.state(output));
			do_test(trampoline.address(), code, locations.length, output);

			return ExecutionResult.ofStruct(output);
		}
	}

	public ExecutionResult runBlock(CPUState startState, MemorySegment code) {
		masterPartition.memory().ms().fill((byte) 0);
		masterPartition.stack().fill((byte) 0);

		try (var arena = Arena.ofConfined()) {
			var output = execution_result.allocate(arena);
			startState.toSavedState(execution_result.state(output));
			do_test(trampoline.address(), code, code.byteSize(), output);

			return ExecutionResult.ofStruct(output);
		}
	}

}
