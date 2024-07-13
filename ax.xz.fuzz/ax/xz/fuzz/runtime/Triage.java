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
		var tester = new Tester(0x48);
		var trampoline = tester.trampoline;
		var scratch1 = tester.scratch1;

		var branches = new TestCase.Branch[]{
				new TestCase.Branch(TestCase.BranchType.JGE, 0, 1),
				new TestCase.Branch(TestCase.BranchType.JNS, 0, 2),
				new TestCase.Branch(TestCase.BranchType.JNE, 4, 1),
				new TestCase.Branch(TestCase.BranchType.JNS, 5, 2),
				new TestCase.Branch(TestCase.BranchType.JNS, 2, 4)
		};

		var b1 = block(trampoline, branches,
				new byte[][]{new byte[]{(byte) 0x2e, (byte) 0xc4, (byte) 0xc1, (byte) 0x5d, (byte) 0xe9, (byte) 0xe1, },
						new byte[]{(byte) 0x36, (byte) 0xc4, (byte) 0xc2, (byte) 0x75, (byte) 0x0a, (byte) 0xf0, },
						new byte[]{(byte) 0x36, (byte) 0xc4, (byte) 0xc2, (byte) 0x0d, (byte) 0xae, (byte) 0xfe, },
						new byte[]{(byte) 0x0f, (byte) 0x6f, (byte) 0xff, },
						new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0x03, (byte) 0xc0, },
						new byte[]{(byte) 0xc5, (byte) 0x19, (byte) 0x62, (byte) 0xff, },
						new byte[]{(byte) 0x36, (byte) 0xc5, (byte) 0xf9, (byte) 0xf1, (byte) 0xe9, },
						new byte[]{(byte) 0x80, (byte) 0xd0, (byte) 0xf7, },
						new byte[]{(byte) 0x2e, (byte) 0xf2, (byte) 0x45, (byte) 0x0f, (byte) 0x12, (byte) 0xe3, },
				},

				new byte[][]{new byte[]{(byte) 0xc4, (byte) 0xe2, (byte) 0x79, (byte) 0x1e, (byte) 0xf4, },
						new byte[]{(byte) 0x66, (byte) 0x45, (byte) 0x0f, (byte) 0xd2, (byte) 0xc1, },
						new byte[]{(byte) 0x66, (byte) 0x44, (byte) 0x0f, (byte) 0x63, (byte) 0xf7, },
						new byte[]{(byte) 0x41, (byte) 0x41, (byte) 0x0f, (byte) 0xb7, (byte) 0xd5, },
						new byte[]{(byte) 0xc4, (byte) 0x42, (byte) 0x7d, (byte) 0x1d, (byte) 0xdd, },
						new byte[]{(byte) 0xc5, (byte) 0x19, (byte) 0xfd, (byte) 0xdf, },
						new byte[]{(byte) 0xc4, (byte) 0xc1, (byte) 0x1d, (byte) 0xda, (byte) 0xff, },
						new byte[]{(byte) 0xf3, (byte) 0x44, (byte) 0x0f, (byte) 0x53, (byte) 0xcc, },
						new byte[]{(byte) 0x66, (byte) 0x41, (byte) 0x0f, (byte) 0xea, (byte) 0xc1, },
						new byte[]{(byte) 0xc4, (byte) 0x62, (byte) 0x75, (byte) 0x03, (byte) 0xca, },
						new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0xf4, (byte) 0xeb, },
						new byte[]{(byte) 0xc5, (byte) 0xd7, (byte) 0x7d, (byte) 0xf4, },
						new byte[]{(byte) 0x65, (byte) 0xf2, (byte) 0x44, (byte) 0x0f, (byte) 0x5a, (byte) 0xc0, },
						new byte[]{(byte) 0xc4, (byte) 0xe3, (byte) 0x79, (byte) 0x08, (byte) 0xe1, (byte) 0xa5, },
						new byte[]{(byte) 0x65, (byte) 0x0f, (byte) 0x38, (byte) 0x05, (byte) 0xeb, },
				}
				);

		var b2 = block(trampoline, branches,
				new byte[][]{new byte[]{(byte) 0x2e, (byte) 0xc4, (byte) 0xc1, (byte) 0x5d, (byte) 0xe9, (byte) 0xe1, },
						new byte[]{(byte) 0x36, (byte) 0xc4, (byte) 0xc2, (byte) 0x0d, (byte) 0xae, (byte) 0xfe, },
						new byte[]{(byte) 0x0f, (byte) 0x6f, (byte) 0xff, },
						new byte[]{(byte) 0x36, (byte) 0xc4, (byte) 0xc2, (byte) 0x75, (byte) 0x0a, (byte) 0xf0, },
						new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0x03, (byte) 0xc0, },
						new byte[]{(byte) 0x36, (byte) 0xc5, (byte) 0xf9, (byte) 0xf1, (byte) 0xe9, },
						new byte[]{(byte) 0xc5, (byte) 0x19, (byte) 0x62, (byte) 0xff, },
						new byte[]{(byte) 0x80, (byte) 0xd0, (byte) 0xf7, },
						new byte[]{(byte) 0x2e, (byte) 0xf2, (byte) 0x45, (byte) 0x0f, (byte) 0x12, (byte) 0xe3, },
				},

				new byte[][]{new byte[]{(byte) 0x66, (byte) 0x44, (byte) 0x0f, (byte) 0x63, (byte) 0xf7, },
						new byte[]{(byte) 0xc4, (byte) 0x42, (byte) 0x7d, (byte) 0x1d, (byte) 0xdd, },
						new byte[]{(byte) 0xc5, (byte) 0x19, (byte) 0xfd, (byte) 0xdf, },
						new byte[]{(byte) 0xc4, (byte) 0xc1, (byte) 0x1d, (byte) 0xda, (byte) 0xff, },
						new byte[]{(byte) 0xc4, (byte) 0xe2, (byte) 0x79, (byte) 0x1e, (byte) 0xf4, },
						new byte[]{(byte) 0x66, (byte) 0x45, (byte) 0x0f, (byte) 0xd2, (byte) 0xc1, },
						new byte[]{(byte) 0x41, (byte) 0x41, (byte) 0x0f, (byte) 0xb7, (byte) 0xd5, },
						new byte[]{(byte) 0xf3, (byte) 0x44, (byte) 0x0f, (byte) 0x53, (byte) 0xcc, },
						new byte[]{(byte) 0x66, (byte) 0x41, (byte) 0x0f, (byte) 0xea, (byte) 0xc1, },
						new byte[]{(byte) 0xc4, (byte) 0x62, (byte) 0x75, (byte) 0x03, (byte) 0xca, },
						new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0xf4, (byte) 0xeb, },
						new byte[]{(byte) 0xc5, (byte) 0xd7, (byte) 0x7d, (byte) 0xf4, },
						new byte[]{(byte) 0x65, (byte) 0xf2, (byte) 0x44, (byte) 0x0f, (byte) 0x5a, (byte) 0xc0, },
						new byte[]{(byte) 0xc4, (byte) 0xe3, (byte) 0x79, (byte) 0x08, (byte) 0xe1, (byte) 0xa5, },
						new byte[]{(byte) 0x65, (byte) 0x0f, (byte) 0x38, (byte) 0x05, (byte) 0xeb, },
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
