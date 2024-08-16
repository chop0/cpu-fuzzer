package ax.xz.fuzz.x86.arch;

import ax.xz.fuzz.arch.Architecture;
import ax.xz.fuzz.arch.BranchType;
import ax.xz.fuzz.arch.CPUState;
import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.mutate.Mutator;
import ax.xz.fuzz.runtime.Config;
import ax.xz.fuzz.runtime.ExecutableSequence;
import ax.xz.fuzz.runtime.ExecutionResult;
import ax.xz.fuzz.x86.mutate.PrefixAdder;
import ax.xz.fuzz.x86.mutate.PrefixDuplicator;
import ax.xz.fuzz.x86.mutate.RexAdder;
import ax.xz.fuzz.x86.operand.OpcodeCache;
import com.github.icedland.iced.x86.ICRegister;
import com.github.icedland.iced.x86.asm.AsmRegister64;
import com.github.icedland.iced.x86.asm.CodeAssembler;
import com.github.icedland.iced.x86.asm.CodeAssemblerResult;
import com.github.icedland.iced.x86.asm.CodeLabel;
import com.github.icedland.iced.x86.dec.ByteArrayCodeReader;
import com.github.icedland.iced.x86.dec.Decoder;
import com.github.icedland.iced.x86.fmt.StringOutput;
import com.github.icedland.iced.x86.fmt.nasm.NasmFormatter;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;

import static ax.xz.fuzz.x86.arch.x86RegisterBanks.*;
import static ax.xz.fuzz.x86.arch.x86RegisterDescriptor.*;
import static java.util.concurrent.ForkJoinPool.getCommonPoolParallelism;

public class X86Architecture implements Architecture {
	private final X86UarchInfo uarchInfo;
	private final ObjectPool<CodeAssembler> assemblerCache = ObjectPool.create(() -> new CodeAssembler(64), CodeAssembler::reset, getCommonPoolParallelism());

	private volatile RegisterSet tracked;

	private X86Architecture(X86UarchInfo uarchInfo) {
		this.uarchInfo = uarchInfo;
	}

	@Override
	public x86RegisterDescriptor registerByIndex(int index) {
		return x86RegisterDescriptor.values()[index];
	}

	@Override
	public RegisterDescriptor registerByName(String name) {
		return x86RegisterDescriptor.fromString(name);
	}

	public RegisterSet trackedRegisters() {
		if (tracked == null) {
			var tracked = RegisterSet.of(FS, GS, RFLAGS).union(GPQ);

			if (uarchInfo.supportsAVX512()) {
				tracked = tracked.union(ZMM_AVX512);
			} else if (uarchInfo.supportsAVX()) {
				tracked = tracked.union(YMM_AVX2);
			} else if (uarchInfo.supportsSSE()) {
				tracked = tracked.union(RegisterSet.of(XMM0, XMM1, XMM2, XMM3, XMM4, XMM5, XMM6, XMM7));
			}

			if (uarchInfo.supportsMMX())
				tracked = tracked.union(MM);

			this.tracked = tracked;
		}

		return tracked;
	}

	@Override
	public RegisterSet validRegisters() {
		return uarchInfo.supportsAVX512() ? x86RegisterBanks.ALL_AVX512 : x86RegisterBanks.ALL_AVX2;
	}

	@Override
	public RegisterSet[] subregisterSets() {
		return bankSets;
	}

	public Opcode[] allOpcodes() {
		return OpcodesHolder.allOpcodes;
	}

	@Override
	public RegisterDescriptor defaultCounter() {
		return R15;
	}

	@Override
	public x86RegisterDescriptor stackPointer() {
		return RSP;
	}

	@Override
	public BranchType unconditionalJump() {
		return X86BranchType.JMP;
	}

	@Override
	public BranchType[] allBranchTypes() {
		return X86BranchType.values();
	}

	@Override
	public Mutator[] allMutators() {
		return new Mutator[] {
			new PrefixAdder(),
			new RexAdder(), // TODO: fix
			// new VexAdder(),
			new PrefixDuplicator()
		};
	}

	public int encode(ExecutableSequence sequence, long exitAddress, MemorySegment code, Config config) {
		var counterRegister = config.counterRegister();
		int counterBound = config.branchLimit();

		var blocks = sequence.blocks();
		var branches = sequence.branches();

		var counter = new AsmRegister64(new ICRegister(((x86RegisterDescriptor) counterRegister).icedId()));
		var blockAssembler = assemblerCache.get();

		// 1. our entrypoint
		blockAssembler.xor(counter, counter);
		// exitpoint
		var exit = blockAssembler.createLabel("exit");

		CodeLabel[] blockHeaders = new CodeLabel[blocks.length + 1];
		CodeLabel[] testCaseLocs = new CodeLabel[blocks.length];
		for (int i = 0; i < blockHeaders.length - 1; i++) {
			blockHeaders[i] = blockAssembler.createLabel();
			testCaseLocs[i] = blockAssembler.createLabel();
		}

		blockHeaders[blockHeaders.length - 1] = exit;

		for (int i = 0; i < blocks.length; i++) {
			blockAssembler.label(blockHeaders[i]);

			blockAssembler.cmp(counter, counterBound);
			blockAssembler.jge(exit);
			blockAssembler.inc(counter);

			for (var item : blocks[i].items()) {
				if (item == null)
					throw new IllegalArgumentException("instruction must not be null");

				blockAssembler.db(item.encode(0));
			}

			switch (branches[i].type()) {
				case X86BranchType t ->
					t.perform.accept(blockAssembler, blockHeaders[branches[i].takenIndex()]);
				default ->
					throw new IllegalArgumentException("Unknown branch type (are you trying to replay a test case from the wrong architecture?): " + branches[i].type());
			}

			blockAssembler.jmp(blockHeaders[branches[i].notTakenIndex()]);
		}

		blockAssembler.label(exit);
		blockAssembler.jmp(exitAddress);

		var bb = code.asByteBuffer();
		int initialPosition = bb.position();
		var result = (CodeAssemblerResult) blockAssembler.assemble(bb::put, code.address());

		assemblerCache.put(blockAssembler);

		return bb.position() - initialPosition;
	}

	@Override
	public String disassemble(byte[] code) {
		ByteArrayCodeReader reader = new ByteArrayCodeReader(code);
		Decoder decoder = new Decoder(64, reader);

		var formatter = new NasmFormatter();
		var output = new StringOutput();

		for (var instruction : decoder) {
			formatter.format(instruction, output);
		}

		return output.toString();
	}

	@Override
	public boolean interestingMismatch(ExecutionResult a, ExecutionResult b) {
		if (a instanceof ExecutionResult.Success(CPUState(var A)) && b instanceof ExecutionResult.Success(
			CPUState(var B)
		)) {
			for (var l : A.keySet()) {
				if (!Arrays.equals(A.get(l), B.get(l))) {
					return true;
				}
			}
		}

		return false;
	}

	public static X86Architecture nativeArchitecture() {
		return InstanceHolder.INSTANCE;
	}

	private static final class OpcodesHolder {
		private static final Opcode[] allOpcodes = OpcodeCache.loadOpcodes();
	}

	private static final class InstanceHolder {
		private static final X86Architecture INSTANCE;

		static {
			INSTANCE = new X86Architecture(X86UarchInfo.loadNativeInfo());
			System.out.println("Loaded local uarch: " + INSTANCE.uarchInfo);
		}
	}
}
