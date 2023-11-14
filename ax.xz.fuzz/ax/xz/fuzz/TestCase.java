package ax.xz.fuzz;

import com.github.icedland.iced.x86.CodeWriter;
import com.github.icedland.iced.x86.ICRegister;
import com.github.icedland.iced.x86.asm.AsmRegister64;
import com.github.icedland.iced.x86.asm.CodeAssembler;
import com.github.icedland.iced.x86.asm.CodeLabel;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.util.function.BiConsumer;
import java.util.random.RandomGenerator;

public record TestCase(InterleavedBlock[] blocks, Branch[] branches) {
	static {
		System.loadLibrary("slave");
	}

	public static final MemorySegment TEST_CASE_FINISH = SymbolLookup.loaderLookup().find("test_case_finish").orElseThrow();

	public TestCase {
		if (blocks.length == 0)
			throw new IllegalArgumentException("blocks must not be empty");
	}

	public void encode(long rip, CodeWriter cw, int counterRegister, int counterBound) {
		var assembler = new CodeAssembler(64);

		var counter = new AsmRegister64(new ICRegister(counterRegister));

		// 1. our entrypoint
		assembler.xor(counter, counter);
		// exitpoint
		var exit = assembler.createLabel("exit");

		CodeLabel[] blockHeaders = new CodeLabel[blocks.length + 1];
		for (int i = 0; i < blockHeaders.length - 1; i++) {
			blockHeaders[i] = assembler.createLabel();
		}
		blockHeaders[blockHeaders.length - 1] = exit;

		for (int i = 0; i < blocks.length; i++) {
			assembler.label(blockHeaders[i]);

			assembler.cmp(counter, counterBound);
			assembler.jge(exit);
			assembler.inc(counter);

			for (var insn : blocks[i].instructions()) {
				assembler.addInstruction(insn);
			}

			branches[i].type.perform.accept(assembler, blockHeaders[branches[i].takenIndex]);
			assembler.jmp(blockHeaders[branches[i].notTakenIndex]);
		}

		assembler.label(exit);
		assembler.jmp(TEST_CASE_FINISH.address());

		var result = assembler.assemble(cw, rip);
	}

	record Branch(BranchType type, int takenIndex, int notTakenIndex) {
		@Override
		public String toString() {
			return STR."""
					\{type.name().toLowerCase()} \{takenIndex}
					jmp \{notTakenIndex}""";
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
		JNP(CodeAssembler::jnp)
		;
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
}
