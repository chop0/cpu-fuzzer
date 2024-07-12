package ax.xz.fuzz.runtime;

import com.github.icedland.iced.x86.asm.CodeAssembler;
import com.github.icedland.iced.x86.asm.CodeLabel;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static ax.xz.fuzz.runtime.TestCase.TEST_CASE_FINISH;
import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.asm.AsmRegisters.*;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class Triage {
	interface CodeBlock {
		void assemble(CodeAssembler assembler);
	}

	private static MemorySegment block(Trampoline trampoline, TestCase.Branch[] branches, byte[][]... blocks) {
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
			assembler.mov(mem_ptr(999999999), rax);

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

	public static void main(String[] args) {
		var tester = new Tester(0);
		var trampoline = tester.trampoline;
		var scratch1 = tester.scratch1;

		var branches = new TestCase.Branch[]{
				new TestCase.Branch(TestCase.BranchType.JE, 1, 1),
				new TestCase.Branch(TestCase.BranchType.JNE, 5, 2),
				new TestCase.Branch(TestCase.BranchType.JNE, 4, 1),
				new TestCase.Branch(TestCase.BranchType.JNS, 5, 2),
				new TestCase.Branch(TestCase.BranchType.JNS, 2, 4)
		};

		var b1 = block(trampoline, branches,
				new byte[][]{
						new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0x3a, (byte) 0x15, (byte) 0xe1, (byte) 0xea, },
						new byte[]{(byte) 0xf3, (byte) 0x0f, (byte) 0x01, (byte) 0xee, },

						new byte[]{(byte) 0x62, (byte) 0xf1, (byte) 0x97, (byte) 0x00, (byte) 0x7b, (byte) 0xe2, },



				}
				);

		var b2 = block(trampoline, branches,
				new byte[][]{new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0x3a, (byte) 0x15, (byte) 0xe1, (byte) 0xea, },
						new byte[]{(byte) 0x62, (byte) 0xf1, (byte) 0x97, (byte) 0x00, (byte) 0x7b, (byte) 0xe2, },
						new byte[]{(byte) 0xf3, (byte) 0x0f, (byte) 0x01, (byte) 0xee, },
					
				}
				);

		// print b1 and b2 in hex
		for (var b : b1.toArray(JAVA_BYTE)) {
			System.out.printf("%02x ", b & 0xff);
		}
		System.out.println();

		for (var b : b2.toArray(JAVA_BYTE)) {
			System.out.printf("%02x ", b & 0xff);
		}
		System.out.println();

		var result1 = tester.runBlock(CPUState.filledWith(scratch1.address()), b1);
		scratch1.fill((byte) 0);
		var result2 = tester.runBlock(CPUState.filledWith(scratch1.address()), b2);

		System.out.println(result1);
		System.out.println(result2);

		System.out.println(ExecutionResult.interestingMismatch(result1, result2));
	}
}
