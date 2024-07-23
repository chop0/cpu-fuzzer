package ax.xz.fuzz.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.icedland.iced.x86.Code;
import com.github.icedland.iced.x86.ICRegister;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.Register;
import com.github.icedland.iced.x86.asm.AsmRegisterXMM;
import com.github.icedland.iced.x86.asm.CodeAssembler;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ax.xz.fuzz.runtime.ExecutableSequence.TEST_CASE_FINISH;
import static ax.xz.fuzz.runtime.ExecutableSequence.encode;
import static ax.xz.fuzz.runtime.MemoryUtils.Protection.*;
import static com.github.icedland.iced.x86.asm.AsmRegisters.*;
import static java.lang.foreign.MemorySegment.NULL;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

public class Triage2 {
	interface CodeBlock {
		void assemble(CodeAssembler assembler);
	}

	private static MemorySegment block(Arena arena, MemorySegment a, MemorySegment b, MemorySegment c, Trampoline trampoline, boolean fences, AsmRegisterXMM r0, AsmRegisterXMM r1, AsmRegisterXMM r2, AsmRegisterXMM r3) {
		var seg = MemoryUtils.mmap(arena, NULL, 4096, READ, WRITE, EXECUTE);
		var assembler = new CodeAssembler(64);
		var assemblerInsn = new CodeAssembler(64);

		assemblerInsn.reset();
		assembler.db(encode(assemblerInsn, Instruction.create(Code.EVEX_VMOVUPS_XMM_K1Z_XMMM128, r1.get(), xmmword_ptr(c.address()).toMemoryOperand(64))));
		assemblerInsn.reset();
		assembler.db(encode(assemblerInsn, Instruction.create(Code.EVEX_VPXORD_XMM_K1Z_XMM_XMMM128B32, r0.get(), r0.get(), r0.get())));

//		assembler.xor(eax, eax);
//		assembler.cpuid();

		assembler.or(r11, r11);
		assembler.or(r11, r11);

		if (fences) assembler.mfence();
//		assembler.vmovlps(r0, r1, qword_ptr(mem1));
		assemblerInsn.reset();
		assembler.db(encode(assemblerInsn, Instruction.create(Code.EVEX_VMOVLPS_XMM_XMM_M64, r0.get(), r1.get(), qword_ptr(a.address()).toMemoryOperand(64))));
		if (fences) assembler.mfence();
//		assembler.vmovhps(r2, r3, xmmword_ptr(mem2));
		assemblerInsn.reset();
		assembler.db(encode(assemblerInsn, Instruction.create(Code.EVEX_VMOVHPS_XMM_XMM_M64, r2.get(), r3.get(), qword_ptr(b.address()).toMemoryOperand(64))));
		if (fences) assembler.mfence();

		assembler.jmp(trampoline.relocate(TEST_CASE_FINISH).address());

		var buf = seg.asByteBuffer();
		assembler.assemble(buf::put, seg.address());
		return seg.asSlice(0, buf.position());
	}

	private static boolean test(Tester tester, AsmRegisterXMM r0, AsmRegisterXMM r1, AsmRegisterXMM r2, AsmRegisterXMM r3) {
		try (var arena = Arena.ofConfined()) {
			var a = tester.scratch1;
			var b = tester.scratch2;
			var c = tester.scratch2.asSlice(128);

			a.setAtIndex(JAVA_LONG, 0, 0xdeadbeefdeadbeefL);
			b.setAtIndex(JAVA_LONG, 0, 0x4041424344454647L);

			c.setAtIndex(JAVA_LONG, 0, 0x1111223344556677L);
			c.setAtIndex(JAVA_LONG, 1, 0x8899aabbccddeeffL);

			var withoutFences = block(arena, a, b, c, tester.trampoline, false, r0, r1, r2, r3);
//			var withFences = block(arena, a, b, c, tester.trampoline, true, r0, r1, r2, r3);

			var resultA = (ExecutionResult.Success) tester.runBlock(CPUState.filledWith(0L), withoutFences);
//			var resultB = (ExecutionResult.Success) tester.runBlock(CPUState.filledWith(0L), withFences);

			var output = resultA.state().zmm().zmm()[r0.getRegister() - Register.XMM0];
			return output[8] != (byte)0xff;
		}
	}

	private static boolean allTrue(boolean[] results, int offset, int length, int... except) {
		outer: for (int i = offset; i < offset + length; i++) {
			for (int j : except) {
				if (i == j) continue outer;
			}

			if (!results[i]) return false;
		}
		return true;
	}

	private static boolean allFalse(boolean[] results, int offset, int length) {
		for (int i = offset; i < offset + length; i++) {
			if (results[i]) return false;
		}
		return true;
	}

	private static String findWorkingExtensions(boolean[] results, int i, int j, int k) {
		var labels = new ArrayList<String>();
		labels.addAll(getLabels(results, "XMM[0-7]", 0, 8, i, j, k));
		labels.addAll(getLabels(results, "XMM[8-15]", 8, 8, i, j, k));
		labels.addAll(getLabels(results, "XMM[16-31]", 16, 16, i, j, k));

		return String.join(" | ", labels);
	}

	private static List<String> getLabels(boolean[] results, String name, int offset, int length, int i_, int j, int k) {
		if (allTrue(results, offset, length))
			return List.of(name);
		else if (!allFalse(results, offset, length)) {
			var labels = new ArrayList<String>();
			for (int i = 0; i < length; i++) {
				if ((i + offset) == i_ || (i + offset) == j || (i + offset) == k) continue;
				if (results[i + offset]) {
					labels.add("XMM" + (i + offset));
				}
			}
			return labels;
		} else return List.of();
	}

	public static void main(String[] args) throws JsonProcessingException {
		boolean[][][][] result = new boolean[32][32][32][32];

		var tester = new Tester(0);

		for (int i = 0; i < 32; i++) {
			for (int j = 0; j < 32; j++) {
				if (i == j) continue;

				double percentDone = (i * 31 * 30 * 29 + j * 30 * 29) / (32.0 * 31 * 30 * 29) * 100;
				System.err.println("Progress: " + percentDone + "%");

				for (int k = 0; k < 32; k++) {
					if (i == k || j == k) continue;
					for (int l = 0; l < 32; l++) {
						if (i == l || j == l || k == l) continue;
						var xmm0 = new AsmRegisterXMM(new ICRegister(Register.XMM0 + i));
						var xmm1 = new AsmRegisterXMM(new ICRegister(Register.XMM0 + j));
						var xmm2 = new AsmRegisterXMM(new ICRegister(Register.XMM0 + k));
						var xmm3 = new AsmRegisterXMM(new ICRegister(Register.XMM0 + l));
						result[i][j][k][l] = test(tester, xmm0, xmm1, xmm2, xmm3);
					}

					var ext = findWorkingExtensions(result[i][j][k], i, j, k);
					if (!ext.isBlank())
						System.out.println("output: XMM" + i + ", input: XMM" + j + ", scratch_dst: XMM" + k + ", scratch_src: " + ext);
				}
			}
		}
	}
}
