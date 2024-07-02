package ax.xz.fuzz;

import com.github.icedland.iced.x86.*;
import com.github.icedland.iced.x86.asm.AsmRegister64;
import com.github.icedland.iced.x86.asm.CodeAssembler;
import com.github.icedland.iced.x86.asm.CodeAssemblerResult;
import com.github.icedland.iced.x86.asm.CodeLabel;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.random.RandomGenerator;

public final class TestCase {
	static {
		System.loadLibrary("slave");
	}

	public static final MemorySegment TEST_CASE_FINISH = SymbolLookup.loaderLookup().find("test_case_finish").orElseThrow();
	private final InterleavedBlock[] blocks;
	private final Branch[] branches;


	public TestCase(InterleavedBlock[] blocks, Branch[] branches) {
		if (blocks.length == 0)
			throw new IllegalArgumentException("blocks must not be empty");
		this.blocks = blocks;
		this.branches = branches;
	}

	private byte[] encode(ResourcePartition rp, Instruction instruction, RandomGenerator rng) {
		byte[] result = new byte[15];
		var buf = ByteBuffer.wrap(result);
		var ca = new CodeAssembler(64);
		ca.addInstruction(instruction);
		ca.assemble(buf::put, 0);
		var trimmed = new byte[buf.position()];
		System.arraycopy(result, 0, trimmed, 0, trimmed.length);

		return trimmed;
	}

	public void encode(RandomGenerator rng, long rip, CodeWriter cw, int counterRegister, int counterBound) {
		var assembler = new CodeAssembler(64);

		var counter = new AsmRegister64(new ICRegister(counterRegister));

		// 1. our entrypoint
		assembler.xor(counter, counter);
		// exitpoint
		var exit = assembler.createLabel("exit");

		CodeLabel[] blockHeaders = new CodeLabel[blocks.length + 1];
		CodeLabel[] testCaseLocs = new CodeLabel[blocks.length];
		for (int i = 0; i < blockHeaders.length - 1; i++) {
			blockHeaders[i] = assembler.createLabel();
			testCaseLocs[i] = assembler.createLabel();
		}

		blockHeaders[blockHeaders.length - 1] = exit;

		for (int i = 0; i < blocks.length; i++) {
			assembler.label(blockHeaders[i]);

			assembler.cmp(counter, counterBound);
			assembler.jge(exit);
			assembler.inc(counter);

			Instruction[] instructions = blocks[i].instructions();
			for (int j = 0; j < instructions.length; j++) {
				if (instructions[j] == null)
					throw new IllegalArgumentException("instruction must not be null");

				var insn = instructions[j];

				var encoded = encode(blocks[i].partitionOf(j), insn, rng);

				for (var mutation : blocks[i].mutations()[j]) {
					encoded = mutation.perform(encoded);
				}

				assembler.db(encoded);
			}

			branches[i].type.perform.accept(assembler, blockHeaders[branches[i].takenIndex]);
			assembler.jmp(blockHeaders[branches[i].notTakenIndex]);
		}

		assembler.label(exit);
		assembler.jmp(TEST_CASE_FINISH.address());

		var result = (CodeAssemblerResult) assembler.assemble(cw, rip);
	}

	record Branch(BranchType type, int takenIndex, int notTakenIndex) {
		@Override
		public String toString() {
			return """
					%s %d
					jmp %d""".formatted(type.name().toLowerCase(), takenIndex, notTakenIndex);
		}
	}

	static BranchType randomBranch(RandomGenerator rng) {
		return BranchType.values()[rng.nextInt(BranchType.values().length)];
	}

	private enum BranchType {
		JO(CodeAssembler::jo),
		JNO(CodeAssembler::jno),
		JS(CodeAssembler::js),
		JNS(CodeAssembler::jns),
		JE(CodeAssembler::je),
		JNE(CodeAssembler::jne),
		JB(CodeAssembler::jb),
		JNB(CodeAssembler::jnb),
		JBE(CodeAssembler::jbe),
		JA(CodeAssembler::ja),
		JL(CodeAssembler::jl),
		JGE(CodeAssembler::jge),
		JLE(CodeAssembler::jle),
		JG(CodeAssembler::jg),
		JP(CodeAssembler::jp),
		JNP(CodeAssembler::jnp);
		public final BiConsumer<CodeAssembler, CodeLabel> perform;

		BranchType(BiConsumer<CodeAssembler, CodeLabel> perform) {
			this.perform = perform;
		}
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();
		for (int i = 0; i < blocks.length; i++) {
			sb.append(i).append(":\n");
			sb.append(blocks[i].toString()).append("\n");
			sb.append(branches[i].toString());
			sb.append("\n\n");
		}

		return sb.toString();
	}

	public InterleavedBlock[] blocks() {
		return blocks;
	}

	public Branch[] branches() {
		return branches;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (TestCase) obj;
		return Objects.equals(this.blocks, that.blocks) &&
			   Objects.equals(this.branches, that.branches);
	}

	@Override
	public int hashCode() {
		return Objects.hash(blocks, branches);
	}

}
