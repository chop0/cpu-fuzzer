package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.*;
import ax.xz.fuzz.blocks.randomisers.ProgramRandomiser;
import ax.xz.fuzz.instruction.MemoryPartition;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.instruction.StatusFlag;
import ax.xz.fuzz.tester.execution_result;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.SplittableRandom;
import java.util.function.BiFunction;
import java.util.random.RandomGenerator;

import static ax.xz.fuzz.runtime.ExecutionResult.interestingMismatch;
import static ax.xz.fuzz.runtime.MemoryUtils.Protection.*;
import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.Register.*;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class Tester {
	static {
		System.loadLibrary("slave");
	}

	private static final RegisterSet BANNED_REGISTERS = RegisterSet.of(R15L, R15W, R15D, R15);

	public final Trampoline trampoline;

	public final MemorySegment scratch1;
	final MemorySegment scratch2;
	private final MemorySegment code;

	private final ProgramRandomiser randomiser = new ProgramRandomiser();
	 final ResourcePartition masterPartition;

	private final RandomGenerator rng = new SplittableRandom();

	public Tester(int index) {
		var arena = Arena.ofAuto();
		this.scratch1 = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x110000 + index * 4096L * 2), 4096, READ, WRITE);
		this.scratch2 = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x210000 + index * 4096L * 2), 4096, READ, WRITE);
		this.code = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x1310000 + index * 4096L * 16 * 2), 4096 * 16, READ, WRITE, EXECUTE);

		this.trampoline = Trampoline.create(arena);

		this.masterPartition = new ResourcePartition(StatusFlag.all(), RegisterSet.ALL_VEX.subtract(BANNED_REGISTERS), MemoryPartition.of(scratch1, scratch2));
	}

	public Tester(Trampoline trampoline, MemorySegment scratch1, MemorySegment scratch2, MemorySegment code, ResourcePartition masterPartition) {
		this.trampoline = trampoline;
		this.scratch1 = scratch1;
		this.scratch2 = scratch2;
		this.code = code;
		this.masterPartition = masterPartition;
	}

	public static Tester create(RegisterSet registers, EnumSet<StatusFlag> flags) {
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

		var arena = Arena.ofAuto();
		var scratch1 = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x110000 + index * 4096L * 2), 4096, READ, WRITE);
		var scratch2 = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x210000 + index * 4096L * 2), 4096, READ, WRITE);
		var code = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x1310000 + index * 4096L * 16 * 2), 4096 * 16, READ, WRITE, EXECUTE);

		var trampoline = Trampoline.create(arena);

		var masterPartition = new ResourcePartition(flags, registers.subtract(BANNED_REGISTERS), MemoryPartition.of(scratch1, scratch2));
		return new Tester(trampoline, scratch1, scratch2, code, masterPartition);
	}

	private static final Object printLock = new Object();

	public ExecutionResult[] runTest() {
		var tc = randomiser.selectTestCase(rng, masterPartition);
		var results = new ExecutionResult[tc.sequences().length];

		ExecutionResult previousResult = null;
		ExecutableSequence previousBlock = null;
		byte[] previousCode = null;

		ExecutableSequence[] sequences = tc.sequences();
		for (int i = 0; i < sequences.length; i++) {
			var test = sequences[i];
			SequenceResult output = runSequence(tc.initialState(), test);

			if (interestingMismatch(previousResult, output.result())) {
				synchronized (printLock) {
					System.err.println("Found inconsistent behaviour");
					System.err.println(tc);
					System.err.println("--- First result ----");
					System.err.println(previousResult);
					System.err.println("--- Second result ----");
					System.err.println(output.result());

					for (var b : previousCode) {
						System.out.printf("%02x ", b & 0xff);
					}
					System.out.println();

					for (var b : output.codeSlice().toArray(JAVA_BYTE)) {
						System.out.printf("%02x ", b & 0xff);
					}

					System.out.println();

					var minimised = minimise(tc);
					System.err.println("Minimised:");
					System.err.println(minimised);

					var mapper = OpcodeCache.getMapper();
					String value = null;
					try {
						value = mapper.writeValueAsString(minimised.initialState());
					} catch (JsonProcessingException e) {
						throw new RuntimeException(e);
					}
					System.err.println(value);
				}
			}

			results[i] = previousResult = output.result();
			previousBlock = test;
			previousCode = output.codeSlice().toArray(JAVA_BYTE);
		}

		return results;
	}

	private SequenceResult runSequence(CPUState initialState, ExecutableSequence test) {
		scratch1.fill((byte) 0);
		scratch2.fill((byte) 0);

		int codeLength = test.encode(code.address(), trampoline, code, R15, 100);
		code.asSlice(codeLength).fill((byte) 0);

		var codeSlice = code.asSlice(0, codeLength);
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

		for (;;) {
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

		var sequences = new ExecutableSequence[] { new ExecutableSequence(test1, branches), new ExecutableSequence(test2, branches) };
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

			var test1Result = runSequence(startState, new ExecutableSequence(test1Changed, branches));
			var test2Result = runSequence(startState, new ExecutableSequence(test2Changed, branches));

			if (interestingMismatch(test1Result.result, test2Result.result)) {
				redundantIndices.add(i);
			}
		}

		return redundantIndices;
	}

	private CPUState simplifyInitialState(CPUState state, ExecutableSequence... sequences) {
		for (int i = 0; i < 32; i++) {
			var zmm = state.zmm().withZeroed(i);
			var newState = new CPUState(state.gprs(), zmm, state.mmx(), state.rflags());

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
			} else {
				state = newState;
			}
		}

		for (int i = 0; i < 8; i++) {
			var mmx = state.mmx().withZeroed(i);
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
			} else {
				state = newState;
			}
		}

		for (int i = 0; i < 16; i++) {
			var gprs = state.gprs().withZeroed(i);
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
			} else {
				state = newState;
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
		try (var arena = Arena.ofConfined()) {
			var output = execution_result.allocate(arena);
			startState.toSavedState(execution_result.state(output));
			do_test(trampoline.address(), code, code.byteSize(), output);

			return ExecutionResult.ofStruct(output);
		}
	}

}
