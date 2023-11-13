package ax.xz.fuzz;

import com.github.icedland.iced.x86.*;
import com.github.icedland.iced.x86.asm.AsmRegister64;
import com.github.icedland.iced.x86.asm.AsmRegisters;
import com.github.icedland.iced.x86.asm.CodeAssembler;
import com.github.icedland.iced.x86.asm.CodeLabel;
import com.github.icedland.iced.x86.enc.Encoder;
import com.github.icedland.iced.x86.enc.InstructionBlock;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

public record TestCase(CombinedBlock[] blocks, Branch[] branches) {
	static {
		System.loadLibrary("slave");
	}
	private static final MemorySegment TEST_CASE_FINISH = SymbolLookup.loaderLookup().find("test_case_finish").orElseThrow();

	public TestCase {
		if (blocks.length == 0)
			throw new IllegalArgumentException("blocks must not be empty");
	}

	public static TestCase ofBlocks(RandomGenerator rng, CombinedBlock[] blocks) {
		var branches = new Branch[blocks.length];
		for (int i = 0; i < branches.length; i++) {
			branches[i] = new Branch(randomBranch(rng), rng.nextInt(0, blocks.length), rng.nextInt(0, blocks.length));
		}
		return new TestCase(blocks, branches);
	}

	public void encode(int rip, CodeWriter cw, int counterRegister, int counterBound) {
		var assembler = new CodeAssembler(64);

		var counter = new AsmRegister64(new ICRegister(counterRegister));

		// 1. our entrypoint
		assembler.xor(counter, counter);
		// exitpoint
		var exit = assembler.createLabel("exit");

		CodeLabel[] blockHeaders = new CodeLabel[blocks.length];
		for (int i = 0; i < blockHeaders.length; i++) {
			blockHeaders[i] = assembler.createLabel();
		}

		for (int i = 0; i < blocks.length; i++) {
			assembler.label(blockHeaders[i]);

			assembler.cmp(counter, counterBound);
			assembler.jge(exit);
			assembler.inc(counter);

			for (var insn : blocks[i].instructions()) {
				assembler.addInstruction(insn);
			}

			branches[i].type.perform(assembler, blockHeaders[branches[i].takenIndex]);
			assembler.jmp(blockHeaders[branches[i].notTakenIndex]);
		}

		assembler.label(exit);
		assembler.jmp(TEST_CASE_FINISH.address());

		var result = assembler.assemble(cw, rip);
		if (result instanceof String)
			System.out.println(result);
	}

	record Branch(BranchType type, int takenIndex, int notTakenIndex) {}

	static BranchType randomBranch(RandomGenerator rng) {
		BranchType[] options = {
				CodeAssembler::jo,
				CodeAssembler::jno,
				CodeAssembler::js,
				CodeAssembler::jns,
				CodeAssembler::je,
				CodeAssembler::jne,
				CodeAssembler::jb,
				CodeAssembler::jnb,
				CodeAssembler::jbe,
				CodeAssembler::ja,
				CodeAssembler::jl,
				CodeAssembler::jge,
				CodeAssembler::jle,
				CodeAssembler::jg,
				CodeAssembler::jp,
				CodeAssembler::jnp
		};

		return options[rng.nextInt(options.length)];
	}

	private interface BranchType {
		void perform(CodeAssembler assembler, CodeLabel label);
	}
}
