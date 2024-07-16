package ax.xz.fuzz.runtime;

import ax.xz.fuzz.instruction.RegisterSet;
import com.github.icedland.iced.x86.asm.CodeAssembler;
import com.github.icedland.iced.x86.asm.CodeLabel;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Random;
import java.util.SplittableRandom;

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
			System.out.println("Branches: " + branches[i].type() + " " + branches[i].takenIndex() + " " + branches[i].notTakenIndex());
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
				new TestCase.Branch(TestCase.BranchType.JGE, 0, 0),
				new TestCase.Branch(TestCase.BranchType.JNS, 0, 0),
				new TestCase.Branch(TestCase.BranchType.JNE, 4, 1),
				new TestCase.Branch(TestCase.BranchType.JNS, 5, 2),
				new TestCase.Branch(TestCase.BranchType.JNS, 2, 4)
		};

		var b1 = block(trampoline, branches,
			new byte[][]{new byte[]{(byte) 0x26, (byte) 0x48, (byte) 0x83, (byte) 0xc0, (byte) 0x85, },
				new byte[]{(byte) 0x26, (byte) 0xc4, (byte) 0xe2, (byte) 0xd5, (byte) 0xb8, (byte) 0xed, },
				new byte[]{(byte) 0xc4, (byte) 0xe2, (byte) 0x71, (byte) 0xf7, (byte) 0xc9, },
				new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0x29, (byte) 0xed, },
				new byte[]{(byte) 0x26, (byte) 0x26, (byte) 0x0f, (byte) 0x03, (byte) 0xc0, },
				new byte[]{(byte) 0xc6, (byte) 0xc1, (byte) 0x50, },
				new byte[]{(byte) 0x26, (byte) 0x0f, (byte) 0x72, (byte) 0xd5, (byte) 0x93, },
				new byte[]{(byte) 0xc4, (byte) 0xe2, (byte) 0xfd, (byte) 0xa7, (byte) 0xc0, },
				new byte[]{(byte) 0x26, (byte) 0xf3, (byte) 0x4a, (byte) 0x0f, (byte) 0x1e, (byte) 0xfa, },
				new byte[]{(byte) 0x26, (byte) 0x26, (byte) 0x89, (byte) 0xc9, },
				new byte[]{(byte) 0x26, (byte) 0xc4, (byte) 0xe2, (byte) 0x79, (byte) 0x06, (byte) 0xc0, },
				new byte[]{(byte) 0x62, (byte) 0xf3, (byte) 0x7d, (byte) 0x08, (byte) 0x22, (byte) 0xc1, (byte) 0x8b, },
				new byte[]{(byte) 0xc5, (byte) 0xd4, (byte) 0x5c, (byte) 0xed, },
				new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0xf6, (byte) 0xc0, },
				new byte[]{(byte) 0xc5, (byte) 0xfa, (byte) 0x5d, (byte) 0xc0, },
				new byte[]{(byte) 0x26, (byte) 0xc5, (byte) 0xd5, (byte) 0xfc, (byte) 0xed, },
				new byte[]{(byte) 0x62, (byte) 0xf1, (byte) 0xd7, (byte) 0x08, (byte) 0x2a, (byte) 0xe8, },
				new byte[]{(byte) 0xc5, (byte) 0xfb, (byte) 0x12, (byte) 0xc0, },
				new byte[]{(byte) 0x26, (byte) 0x26, (byte) 0xc4, (byte) 0xe2, (byte) 0x79, (byte) 0x3d, (byte) 0xc0, },
				new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0x38, (byte) 0xdc, (byte) 0xed, },
				new byte[]{(byte) 0xc4, (byte) 0xe2, (byte) 0xd1, (byte) 0xbe, (byte) 0xed, },
				new byte[]{(byte) 0xc4, (byte) 0xe3, (byte) 0x79, (byte) 0x14, (byte) 0xc1, (byte) 0xac, },
				new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0x38, (byte) 0x05, (byte) 0xed, },
				new byte[]{(byte) 0x0f, (byte) 0xb7, (byte) 0xc0, },
				new byte[]{(byte) 0xc4, (byte) 0xe2, (byte) 0x7d, (byte) 0x30, (byte) 0xed, },
				new byte[]{(byte) 0xc4, (byte) 0xe2, (byte) 0x79, (byte) 0x13, (byte) 0xed, },
				new byte[]{(byte) 0x26, (byte) 0x66, (byte) 0x13, (byte) 0xc0, },
				new byte[]{(byte) 0x26, (byte) 0x0f, (byte) 0xd1, (byte) 0xed, },
				new byte[]{(byte) 0x0f, (byte) 0xd8, (byte) 0xed, },
				new byte[]{(byte) 0x26, (byte) 0xc4, (byte) 0xe2, (byte) 0xd5, (byte) 0x96, (byte) 0xed, },
				new byte[]{(byte) 0x26, (byte) 0xc5, (byte) 0xd3, (byte) 0x7d, (byte) 0xed, },
			}
			);

		var b2 = block(trampoline, branches,
			new byte[][]{new byte[]{(byte) 0x26, (byte) 0x48, (byte) 0x83, (byte) 0xc0, (byte) 0x85, },
				new byte[]{(byte) 0x26, (byte) 0xc4, (byte) 0xe2, (byte) 0xd5, (byte) 0xb8, (byte) 0xed, },
				new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0x29, (byte) 0xed, },
				new byte[]{(byte) 0xc4, (byte) 0xe2, (byte) 0x71, (byte) 0xf7, (byte) 0xc9, },
				new byte[]{(byte) 0xc6, (byte) 0xc1, (byte) 0x50, },
				new byte[]{(byte) 0x26, (byte) 0x26, (byte) 0x0f, (byte) 0x03, (byte) 0xc0, },
				new byte[]{(byte) 0xc4, (byte) 0xe2, (byte) 0xfd, (byte) 0xa7, (byte) 0xc0, },
				new byte[]{(byte) 0x26, (byte) 0x26, (byte) 0x89, (byte) 0xc9, },
				new byte[]{(byte) 0x26, (byte) 0x0f, (byte) 0x72, (byte) 0xd5, (byte) 0x93, },
				new byte[]{(byte) 0x26, (byte) 0xf3, (byte) 0x4a, (byte) 0x0f, (byte) 0x1e, (byte) 0xfa, },
				new byte[]{(byte) 0x26, (byte) 0xc4, (byte) 0xe2, (byte) 0x79, (byte) 0x06, (byte) 0xc0, },
				new byte[]{(byte) 0x62, (byte) 0xf3, (byte) 0x7d, (byte) 0x08, (byte) 0x22, (byte) 0xc1, (byte) 0x8b, },
				new byte[]{(byte) 0xc5, (byte) 0xd4, (byte) 0x5c, (byte) 0xed, },
				new byte[]{(byte) 0x26, (byte) 0xc5, (byte) 0xd5, (byte) 0xfc, (byte) 0xed, },
				new byte[]{(byte) 0x62, (byte) 0xf1, (byte) 0xd7, (byte) 0x08, (byte) 0x2a, (byte) 0xe8, },
				new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0x38, (byte) 0xdc, (byte) 0xed, },
				new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0xf6, (byte) 0xc0, },
				new byte[]{(byte) 0xc5, (byte) 0xfa, (byte) 0x5d, (byte) 0xc0, },
				new byte[]{(byte) 0xc4, (byte) 0xe2, (byte) 0xd1, (byte) 0xbe, (byte) 0xed, },
				new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0x38, (byte) 0x05, (byte) 0xed, },
				new byte[]{(byte) 0x0f, (byte) 0xb7, (byte) 0xc0, },
				new byte[]{(byte) 0xc4, (byte) 0xe2, (byte) 0x7d, (byte) 0x30, (byte) 0xed, },
				new byte[]{(byte) 0xc5, (byte) 0xfb, (byte) 0x12, (byte) 0xc0, },
				new byte[]{(byte) 0xc4, (byte) 0xe2, (byte) 0x79, (byte) 0x13, (byte) 0xed, },
				new byte[]{(byte) 0x26, (byte) 0x66, (byte) 0x13, (byte) 0xc0, },
				new byte[]{(byte) 0x26, (byte) 0x0f, (byte) 0xd1, (byte) 0xed, },
				new byte[]{(byte) 0x26, (byte) 0x26, (byte) 0xc4, (byte) 0xe2, (byte) 0x79, (byte) 0x3d, (byte) 0xc0, },
				new byte[]{(byte) 0x0f, (byte) 0xd8, (byte) 0xed, },
				new byte[]{(byte) 0x26, (byte) 0xc4, (byte) 0xe2, (byte) 0xd5, (byte) 0x96, (byte) 0xed, },
				new byte[]{(byte) 0x26, (byte) 0xc5, (byte) 0xd3, (byte) 0x7d, (byte) 0xed, },
				new byte[]{(byte) 0xc4, (byte) 0xe3, (byte) 0x79, (byte) 0x14, (byte) 0xc1, (byte) 0xac, },
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
