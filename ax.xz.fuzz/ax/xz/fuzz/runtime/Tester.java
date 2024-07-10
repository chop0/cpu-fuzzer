package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.Block;
import ax.xz.fuzz.blocks.BlockGenerator;
import ax.xz.fuzz.blocks.InterleavedBlock;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.tester.execution_result;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.Random;

import static ax.xz.fuzz.runtime.MemoryUtils.Protection.*;
import static ax.xz.fuzz.runtime.MemoryUtils.assignPkey;
import static ax.xz.fuzz.runtime.TestCase.randomBranch;
import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.Register.*;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class Tester {
	public static final int SCRATCH_PKEY;

	static {
		System.loadLibrary("slave");
		SCRATCH_PKEY = pkey_alloc(0, 0);
		if (SCRATCH_PKEY < 0) {
			throw new RuntimeException("Failed to allocate pkey");
		}

		if (pkey_set(SCRATCH_PKEY, 0) != 0) {
			throw new RuntimeException("Failed to set pkey");
		}

		System.out.println("Scratch pkey: " + SCRATCH_PKEY);
	}

	private static final int NUM_BLOCKS = System.getenv("NUM_BLOCKS") == null ? 5 : Integer.parseInt(System.getenv("NUM_BLOCKS"));
	private static final int NUM_TRIALS = 2;
	private static final RegisterSet BANNED_REGISTERS = RegisterSet.of(R15L, R15W, R15D, R15);

	public final Trampoline trampoline;

	public final MemorySegment scratch1;
	private final MemorySegment scratch2;
	private final MemorySegment code;

	private final Random rng = new Random();

	public Tester(int index) {
		var arena = Arena.ofAuto();
		this.scratch1 = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x110000 + index * 4096L * 2), 4096, READ, WRITE);
		this.scratch2 = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x210000 + index * 4096L * 2), 4096, READ, WRITE);
		this.code = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x1310000 + index * 4096L * 16 * 2), 4096 * 16, READ, WRITE, EXECUTE);

		assignPkey(scratch1, SCRATCH_PKEY);
		assignPkey(scratch2, SCRATCH_PKEY);

		this.trampoline = Trampoline.create(arena);
	}

	private static final Object printLock = new Object();

	public ExecutionResult[] runTest() throws BlockGenerator.NoPossibilitiesException {
		var partitions = ResourcePartition.partitioned(true, rng, scratch1, scratch2);
		var lhsGenerator = new BlockGenerator(partitions[0].withAllowedRegisters(partitions[0].allowedRegisters().subtract(BANNED_REGISTERS)));
		var rhsGenerator = new BlockGenerator(partitions[1].withAllowedRegisters(partitions[1].allowedRegisters().subtract(BANNED_REGISTERS)));

		lhsGenerator.setPartition(partitions[0].withAllowedRegisters(partitions[0].allowedRegisters().subtract(BANNED_REGISTERS)));
		rhsGenerator.setPartition(partitions[1].withAllowedRegisters(partitions[1].allowedRegisters().subtract(BANNED_REGISTERS)));

		var  lhs = new Block[NUM_BLOCKS];
		var rhs = new Block[NUM_BLOCKS];
		for (int i = 0; i < NUM_BLOCKS; i++) {
			lhs[i] = lhsGenerator.createBasicBlock(rng);
			rhs[i] = rhsGenerator.createBasicBlock(rng);
		}

		var branches = new TestCase.Branch[NUM_BLOCKS];
		for (int i = 0; i < NUM_BLOCKS; i++) {
			branches[i] = new TestCase.Branch(randomBranch(rng), rng.nextInt(0, lhs.length + 1), rng.nextInt(0, lhs.length + 1));
		}

		var intialState = CPUState.filledWith(scratch1.address());
		var result = new ExecutionResult[NUM_TRIALS];
		byte[] previousCode = null;

		ExecutionResult previousResult = null;
		TestCase previousBlock = null;

		for (int i = 0; i < NUM_TRIALS; i++) {
			scratch1.fill((byte) 0);
			scratch2.fill((byte) 0);

			var interleaved = shuffle(lhs, rhs);
			var test = new TestCase(interleaved, branches);

			int codeLength = test.encode(code.address(), trampoline, code, R15, 100);
			code.asSlice(codeLength).fill((byte) 0);

			var codeSlice = code.asSlice(0, codeLength);
			result[i] = runBlock(intialState, codeSlice);

			if (ExecutionResult.interestingMismatch(previousResult, result[i])) {
				synchronized (printLock) {
					System.err.println("Found inconsistent behaviour");
					System.err.println("--- LHS ----");
					System.out.println(partitions[0]);
					System.err.println(Arrays.toString(lhs));
					System.err.println("--- RHS ----");
					System.out.println(partitions[1]);
					System.err.println(Arrays.toString(rhs));
					System.err.println("--- First interleaved test ----");
					System.err.println(previousBlock);
					System.err.println("--- Second interleaved test ----");
					System.err.println(test);
					System.err.println("--- First result ----");
					System.err.println(previousResult);
					System.err.println("--- Second result ----");
					System.err.println(result[i]);

					for (var b : previousCode) {
						System.out.printf("%02x ", b & 0xff);
					}
					System.out.println();

					for (var b : codeSlice.toArray(JAVA_BYTE)) {
						System.out.printf("%02x ", b & 0xff);
					}

					System.out.println();


					System.out.println(Arrays.toString(branches));

					Minimiser.minimise(() -> {
						scratch1.fill((byte) 0);
						scratch2.fill((byte) 0);
						}, previousBlock.blocks(), test.blocks(), branches);
					}
			}

			previousResult = result[i];
			previousBlock = test;
			previousCode = codeSlice.toArray(JAVA_BYTE);

		}

		return result;
	}

	private InterleavedBlock[] shuffle(Block[] lhs, Block[] rhs) {
		if (lhs.length != rhs.length) {
			throw new IllegalArgumentException("lhs and rhs must have the same length");
		}

		var result = new InterleavedBlock[lhs.length];
		for (int i = 0; i < lhs.length; i++) {
			result[i] = Block.randomlyInterleaved(rng, lhs[i], rhs[i]);
		}

		return result;
	}

	public static ExecutionResult runBlock(CPUState startState, Block block) throws Block.UnencodeableException {
		try (var arena = Arena.ofConfined()) {
			var code = MemoryUtils.mmap(arena, MemorySegment.NULL, (block.size() + 1) * 15L, READ, WRITE, EXECUTE);
			assignPkey(code, SCRATCH_PKEY);
			pkey_set(SCRATCH_PKEY, 0);

			var trampoline = Trampoline.create(arena);

			int[] locations = block.encode(trampoline, code);

			var output = execution_result.allocate(arena);
			startState.toSavedState(execution_result.state(output));
			do_test(SCRATCH_PKEY, trampoline.address(), code, locations.length, output);

			return ExecutionResult.ofStruct(output);
		}
	}

	public ExecutionResult runBlock(CPUState startState, MemorySegment code) {
		try (var arena = Arena.ofConfined()) {
			var output = execution_result.allocate(arena);
			startState.toSavedState(execution_result.state(output));
			do_test(SCRATCH_PKEY, trampoline.address(), code, code.byteSize(), output);

			return ExecutionResult.ofStruct(output);
		}
	}

}
