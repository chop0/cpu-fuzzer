package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.Block;
import ax.xz.fuzz.blocks.InvarianceTestCase;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.runtime.state.CPUState;
import ax.xz.fuzz.tester.execution_result;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.zip.DataFormatException;

import static ax.xz.fuzz.runtime.RecordedTestCase.*;

import static ax.xz.fuzz.runtime.MemoryUtils.Protection.*;
import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.Register.SS;

public class SequenceExecutor {
	private static final RegisterSet ILLEGAL_REGISTERS = RegisterSet.of(SS);

	private final Arena arena;

	private final MemorySegment primaryScratch;
	private final MemorySegment stack;

	private final MemorySegment code;

	private final MemorySegment[] resettableMemory;
	private final MemorySegment[] memoryTemplate;

	private final Config config;

	public SequenceExecutor(Arena arena, MemorySegment primaryScratch, MemorySegment stack, Config config, MemorySegment code, MemorySegment[] resettableMemory, MemorySegment[] memoryTemplate) {
		this.arena = arena;
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

	public SequenceResult runSequence(CPUState initialState, ExecutableSequence test) {
		MemorySegment codeSlice = null;
		try {
			codeSlice = encode(test);
		} catch (Block.UnencodeableException e) {
			throw new RuntimeException(e);
		}
		var result = runBlock(initialState, codeSlice);

		return new SequenceResult(codeSlice, result);
	}

	private ExecutionResult runBlock(CPUState startState, MemorySegment code) {
		for (int i = 0; i < resettableMemory.length; i++) {
			var segment = resettableMemory[i];
			if (memoryTemplate[i] != null)
				memoryTemplate[i].copyFrom(segment);
			else
				segment.fill((byte) 0);
		}

		ExecutionResult result;
		try (var arena = Arena.ofConfined()) {
			var output = execution_result.allocate(arena);
			startState.toSavedState(execution_result.state(output));
			do_test(code, code.byteSize(), output);

			result = ExecutionResult.ofStruct(output);
		}

		return result;
	}

	public RecordedTestCase record(InvarianceTestCase tc) {
		var serializedRegion = new SerialisedRegion[resettableMemory.length];
		for (int i = 0; i < resettableMemory.length; i++) {
			serializedRegion[i] = SerialisedRegion.ofRegion(resettableMemory[i]);
		}

		try {
			return new RecordedTestCase(tc.initialState(), encodeRelevantInstructions(tc.a()), encodeRelevantInstructions(tc.b()),  code.address(), tc.branches(), serializedRegion);
		} catch (Block.UnencodeableException e) {
			throw new RuntimeException(e);
		}
	}

	public RegisterSet legallyModifiableRegisters() {
		return Architecture.supportedRegisters().subtract(RegisterSet.of(SS, config.counterRegister()));
	}

	public MemorySegment primaryScratch() {
		return primaryScratch;
	}

	public MemorySegment stack() {
		return stack;
	}

	private MemorySegment encode(ExecutableSequence sequence) throws Block.UnencodeableException {
		int codeLength = sequence.encode(code, config);
		return code.asSlice(0, codeLength);
	}

	private byte[][][] encodeRelevantInstructions(ExecutableSequence seq) throws Block.UnencodeableException {
		var result = new byte[seq.blocks().length][][];
		Block[] blocks = seq.blocks();
		for (int i = 0; i < blocks.length; i++) {
			var block = blocks[i];
			result[i] = new byte[block.size()][];

			int j = 0;
			for (var item : block.items()) {
				result[i][j++] = item.encode(0);
			}
		}

		return result;
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

		maybe_allocate_signal_stack();
		return new SequenceExecutor(arena, scratch, stack, config, code, new MemorySegment[]{scratch, stack}, new MemorySegment[]{null, null});
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

		maybe_allocate_signal_stack();
		return new SequenceExecutor(arena, null, null, config, code, scratchRegions, templateRegions);
	}

	public record SequenceResult(MemorySegment codeSlice, ExecutionResult result) {
	}
}