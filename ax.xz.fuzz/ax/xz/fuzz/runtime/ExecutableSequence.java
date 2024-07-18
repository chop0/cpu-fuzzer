package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.Block;
import com.github.icedland.iced.x86.ICRegister;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.asm.AsmRegister64;
import com.github.icedland.iced.x86.asm.CodeAssembler;
import com.github.icedland.iced.x86.asm.CodeAssemblerResult;
import com.github.icedland.iced.x86.asm.CodeLabel;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.random.RandomGenerator;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public final class ExecutableSequence {
	static {
		System.loadLibrary("slave");
	}

	public static final MemorySegment TEST_CASE_FINISH = SymbolLookup.loaderLookup().find("test_case_exit").orElseThrow();
	private final Block[] blocks;
	private final Branch[] branches;

	private final CodeAssembler blockAssembler = new CodeAssembler(64);
	private final CodeAssembler instructionAssembler = new CodeAssembler(64);

	private MemorySegment lastCode;

	public ExecutableSequence(Block[] blocks, Branch[] branches) {
		if (blocks.length == 0)
			throw new IllegalArgumentException("blocks must not be empty");
		this.blocks = new Block[blocks.length];
		this.branches = new Branch[branches.length];

		System.arraycopy(blocks, 0, this.blocks, 0, blocks.length);
		System.arraycopy(branches, 0, this.branches, 0, branches.length);
	}


	public static byte[] encode(CodeAssembler instructionAssembler, Instruction instruction) {
		byte[] result = new byte[15];
		var buf = ByteBuffer.wrap(result);
		instructionAssembler.addInstruction(instruction);
		instructionAssembler.assemble(buf::put, 0);
		var trimmed = new byte[buf.position()];
		System.arraycopy(result, 0, trimmed, 0, trimmed.length);

		return trimmed;
	}

	public int encode(long rip, Trampoline trampoline, MemorySegment code, int counterRegister, int counterBound) {
		var counter = new AsmRegister64(new ICRegister(counterRegister));
		blockAssembler.reset();

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

				var insn = item.instruction();

				instructionAssembler.reset();
				var encoded = encode(instructionAssembler, insn);

				for (var mutation : item.mutations()) {
					encoded = mutation.perform(encoded);
				}

				blockAssembler.db(encoded);
			}

			branches[i].type().perform.accept(blockAssembler, blockHeaders[branches[i].takenIndex()]);
			blockAssembler.jmp(blockHeaders[branches[i].notTakenIndex()]);
		}

		blockAssembler.label(exit);
		blockAssembler.jmp(trampoline.relocate(TEST_CASE_FINISH).address());

		var bb = code.asByteBuffer();
		int initialPosition = bb.position();
		var result = (CodeAssemblerResult) blockAssembler.assemble(bb::put, rip);
		lastCode = code.asSlice(initialPosition, bb.position());
		return bb.position() - initialPosition;
	}

	public enum BranchType {
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

		if (lastCode != null) {
			sb.append("new byte[][]{{");
			for (int i = 0; i < lastCode.byteSize(); i++) {
				sb.append(String.format("0x%02x, ", lastCode.get(JAVA_BYTE, i)));
			}
			sb.append("}}\n\n");
		} else {
			for (Block block : blocks) {
				sb.append("new byte[][]{").append(block.toString()).append("},");
//			sb.append(branches[i].toString());
				sb.append("\n\n");
			}
		}

		return sb.toString();
	}

	public Block[] blocks() {
		return blocks;
	}

	public Branch[] branches() {
		return branches;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (ExecutableSequence) obj;
		return Arrays.equals(this.blocks, that.blocks) &&
		       Arrays.equals(this.branches, that.branches);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(blocks), Arrays.hashCode(branches));
	}

}
