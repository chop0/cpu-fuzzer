package ax.xz.fuzz.runtime;

import com.github.icedland.iced.x86.asm.CodeAssembler;
import com.github.icedland.iced.x86.asm.CodeLabel;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;

import static ax.xz.fuzz.runtime.ExecutableSequence.TEST_CASE_FINISH;
import static ax.xz.fuzz.runtime.ExecutionResult.interestingMismatch;
import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.asm.AsmRegisters.r15;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class Triage {
	interface CodeBlock {
		void assemble(CodeAssembler assembler);
	}

	private static MemorySegment block(Trampoline trampoline, Branch[] branches, byte[][]... blocks) {
		var seg = mmap(MemorySegment.NULL, 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(), MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0)
			.reinterpret(4096, Arena.ofAuto(), ms -> munmap(ms, 4096));
		var assembler = new CodeAssembler(64);

		// 1. our entrypoint
		assembler.xor(r15, r15);
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

			assembler.cmp(r15, 100);
			assembler.jge(exit);
			assembler.inc(r15);

			for (var item : blocks) {
				if (item == null)
					throw new IllegalArgumentException("instruction must not be null");

				for (var insn : item) {
					assembler.db(insn);
				}
			}

			branches[i].type().perform.accept(assembler, blockHeaders[branches[i].takenIndex()]);
			assembler.jmp(blockHeaders[branches[i].notTakenIndex()]);
		}

		assembler.label(exit);
		assembler.jmp(trampoline.relocate(TEST_CASE_FINISH).address());

		var buf = seg.asByteBuffer();
		assembler.assemble(buf::put, seg.address());
		return seg.asSlice(0, buf.position());
	}

	public static void runFile(Path file) throws IOException {
		var tc = RecordedTestCase.fromXML(Files.readString(file));

		var tester = Tester.forRecordedCase(Config.defaultConfig(), tc);

		var trampoline = tester.trampoline;
		var branches = tc.branches();

		var b1 = block(trampoline, branches, tc.code1());
		var b2 = block(trampoline, branches, tc.code2());

		var result1 = tester.runBlock(tc.initialState(), b1);
		var result2 = tester.runBlock(tc.initialState(), b2);

		System.out.println(result1);
		System.out.println(result2);
		System.out.println(interestingMismatch(result1, result2));
		if (!interestingMismatch(result1, result2))
			System.exit(1);
	}
}
