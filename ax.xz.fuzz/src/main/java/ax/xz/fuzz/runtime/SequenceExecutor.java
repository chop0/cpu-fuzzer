package ax.xz.fuzz.runtime;

import ax.xz.fuzz.arch.Architecture;
import ax.xz.fuzz.arch.CPUState;
import ax.xz.fuzz.blocks.Block;
import ax.xz.fuzz.blocks.InvarianceTestCase;
import ax.xz.fuzz.mman.MemoryUtils;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.zip.DataFormatException;

import static ax.xz.fuzz.arch.Architecture.nativeArch;
import static ax.xz.fuzz.mman.MemoryUtils.Protection.*;
import static ax.xz.fuzz.runtime.RecordedTestCase.SerialisedRegion;
import static ax.xz.fuzz.runtime.SegmentExecutor.nativeExecutor;

public class SequenceExecutor {
	private final Arena arena;

	private final Architecture architecture;
	private final SegmentExecutor segmentExecutor;

	private final MemorySegment primaryScratch;
	private final MemorySegment stack;

	private final MemorySegment code;

	private final MemorySegment[] resettableMemory;
	private final MemorySegment[] memoryTemplate;

	private final Config config;

	public SequenceExecutor(Arena arena, Architecture architecture, SegmentExecutor segmentExecutor, MemorySegment primaryScratch, MemorySegment stack, Config config, MemorySegment code, MemorySegment[] resettableMemory, MemorySegment[] memoryTemplate) {
		this.arena = arena;
		this.architecture = architecture;
		this.segmentExecutor = segmentExecutor;
		this.primaryScratch = primaryScratch;
		this.stack = stack;
		this.code = code;
		this.config = config;
		this.resettableMemory = resettableMemory;
		this.memoryTemplate = memoryTemplate;

		for (int i = 0; i < memoryTemplate.length; i++) {
			if (memoryTemplate[i] == null) continue;
			if (memoryTemplate[i].byteSize() != resettableMemory[i].byteSize()) {
				throw new IllegalArgumentException("Memory template and zeroing region must be the same size");
			}
		}
	}

	public boolean lookForMismatch(TestCase tc, int attempts) {
		var seqA = new ExecutableSequence(tc.blocksA(), tc.branches());
		var seqB = new ExecutableSequence(tc.blocksB(), tc.branches());

		ExecutionResult lastResult = runSequence(tc.initialState(), seqA).result();

		for (int i = 0; i < attempts; i++) {
			var result2 = runSequence(tc.initialState(), seqB).result();

			if (!lastResult.equals(result2)) {
				return true;
			}
		}

		return false;
	}

	public SequenceResult runSequence(CPUState initialState, ExecutableSequence test) {
		MemorySegment codeSlice = null;
		try {
			codeSlice = encode(test);
		} catch (Block.UnencodeableException e) {
			throw new RuntimeException(e);
		}
		formatMemory();

		var result = segmentExecutor.runCode(codeSlice, initialState);

		return new SequenceResult(codeSlice, result);
	}

	private MemorySegment encode(ExecutableSequence sequence) throws Block.UnencodeableException {
		int codeLength = architecture.encode(sequence, segmentExecutor.okExitAddress(), code, config);
		return code.asSlice(0, codeLength);
	}

	private void formatMemory() {
		for (int i = 0; i < resettableMemory.length; i++) {
			var segment = resettableMemory[i];
			segment.fill((byte) 0);
		}
	}

	public RecordedTestCase record(InvarianceTestCase tc) {
		var serializedRegion = new SerialisedRegion[resettableMemory.length];
		for (int i = 0; i < resettableMemory.length; i++) {
			serializedRegion[i] = SerialisedRegion.ofRegion(resettableMemory[i]);
		}

		return new RecordedTestCase(tc.initialState(), tc.a().blocks(), tc.b().blocks(), code.address(), tc.branches(), serializedRegion);
	}

	public MemorySegment primaryScratch() {
		return primaryScratch;
	}

	public MemorySegment stack() {
		return stack;
	}

	public static SequenceExecutor create(Config config) {
		class holder {
			private static final VarHandle indexHandle;
			private static volatile long index = 0;

			static {
				try {
					indexHandle = MethodHandles.lookup().findStaticVarHandle(holder.class, "index", long.class);
				} catch (NoSuchFieldException | IllegalAccessException e) {
					throw new AssertionError(e);
				}
			}

			private static long nextIndex() {
				return (long) indexHandle.getAndAdd(1);
			}
		}

		long index = holder.nextIndex();

		return withIndex(config, index);
	}

	public static SequenceExecutor withIndex(Config config, long index) {
		var arena = Arena.ofConfined();
		var scratch = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x1100000 + index * 8192L * 2), 8192, READ, WRITE);

		var code = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x1310000 + index * 4096L * 16 * 2), 4096 * 16, READ, WRITE, EXECUTE);
		var stack = MemoryUtils.mmap(arena, MemorySegment.ofAddress(0x6a30000 + index * 4096L * 16 * 2), 4096, READ, WRITE, EXECUTE);

		return new SequenceExecutor(arena, nativeArch(), nativeExecutor(), scratch, stack, config, code, new MemorySegment[]{scratch, stack}, new MemorySegment[]{null, null});
	}

	public static SequenceExecutor forRecordedCase(Config config, RecordedTestCase rtc) throws DataFormatException {
		var arena = Arena.ofConfined();

		var scratchRegions = new MemorySegment[rtc.memory().length];
		var templateRegions = new MemorySegment[rtc.memory().length];
		SerialisedRegion[] memory = rtc.memory();
		for (int i = 0; i < memory.length; i++) {
			var scratch = memory[i];
			scratchRegions[i] = scratch.allocate(arena);
			templateRegions[i] = arena.allocate(scratch.size());
			templateRegions[i].copyFrom(scratchRegions[i]);
		}

		var code = MemoryUtils.mmap(arena, MemorySegment.ofAddress(rtc.codeLocation()), 4096 * 16, READ, WRITE, EXECUTE);

		return new SequenceExecutor(arena, nativeArch(), nativeExecutor(), null, null, config, code, scratchRegions, templateRegions);
	}

	public record SequenceResult(MemorySegment codeSlice, ExecutionResult result) {
	}
}
