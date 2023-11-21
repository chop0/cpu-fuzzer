package ax.xz.fuzz;

import com.github.icedland.iced.x86.asm.CodeAssembler;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.asm.AsmRegisters.*;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class Triage {
	private static MemorySegment block1() {
		var seg = mmap(MemorySegment.NULL, 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(), MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0)
				.reinterpret(4096, Arena.ofAuto(), ms -> munmap(ms, 4096));
		var assembler = new CodeAssembler(64);

/**
 0:
 sub ax,0B98Fh
 rcl cx,1
 pmovsxwq xmm5,xmm6
 vpavgb ymm5,ymm13,ymm6
 js 1
 jmp 1

 1:
 vpsrlq xmm5,xmm9,36h
 xgetbv
 psignw xmm1,xmm10
 js 1
 jmp 2
 */
	var zero = assembler.createLabel();
	var one = assembler.createLabel();

	assembler.label(zero);
	assembler.sub(ax, 0xB98F);
	assembler.rcl(cx, 1);
	assembler.pmovsxwq(xmm5, xmm6);
	assembler.vpavgb(ymm5, ymm13, ymm6);
	assembler.js(one);
	assembler.jmp(one);

	assembler.label(one);
	assembler.vpsrlq(xmm5, xmm9, 0x36);
	assembler.xgetbv();
	assembler.psignw(xmm1, xmm10);
	assembler.js(one);
		assembler.jmp(TestCase.TEST_CASE_FINISH.address());

		var buf = seg.asByteBuffer();
		assembler.assemble(buf::put, 0);
		return seg.asSlice(0, buf.position());
	}

	public static MemorySegment block2() {
		var seg = mmap(MemorySegment.NULL, 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(), MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0)
				.reinterpret(4096, Arena.ofAuto(), ms -> munmap(ms, 4096));

		var assembler = new CodeAssembler(64);

		/**
		 0:
		 sub ax,0B98Fh
		 rcl cx,1
		 pmovsxwq xmm5,xmm6
		 vpavgb ymm5,ymm13,ymm6
		 js 1
		 jmp 1

		 1:
		 xgetbv
		 vpsrlq xmm5,xmm9,36h
		 psignw xmm1,xmm10
		 js 1
		 jmp 2


		 */
		var zero = assembler.createLabel();
		var one = assembler.createLabel();

		assembler.label(zero);
		assembler.sub(ax, 0xB98F);
		assembler.rcl(cx, 1);
		assembler.pmovsxwq(xmm5, xmm6);
		assembler.vpavgb(ymm5, ymm13, ymm6);
		assembler.js(one);
		assembler.jmp(one);

		assembler.label(one);
		assembler.xgetbv();
		assembler.vpsrlq(xmm5, xmm9, 0x36);
		assembler.psignw(xmm1, xmm10);
		assembler.js(one);
		assembler.jmp(TestCase.TEST_CASE_FINISH.address());


		var buf = seg.asByteBuffer();
		assembler.assemble(buf::put, 0);
		return seg.asSlice(0, buf.position());
	}


	public static void main(String[] args) throws BlockGenerator.NoPossibilitiesException, BasicBlock.UnencodeableException {
		// void do_test(uint8_t *code, size_t code_length, struct execution_result *output)

		var scratch1 = mmap(MemorySegment.ofAddress(0x10000000), 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(),
				MAP_FIXED() | MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0).reinterpret(4096);
		if (scratch1.address() == MAP_FAILED().address())
			throw new RuntimeException("mmap failed");

		var scratch2 = mmap(MemorySegment.ofAddress(0x20000000), 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(),
				MAP_FIXED() | MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0).reinterpret(4096);
		if (scratch2.address() == MAP_FAILED().address())
			throw new RuntimeException("mmap failed");

		scratch1.fill((byte)0);
		scratch2.fill((byte)0);

		var result1 = Tester.runBlock(CPUState.filledWith(0), block1());
		System.out.println(result1);

		var b1 = block1();
		var b2 = block2();

		// print block1 in hex
		for (int i = 0; i < b1.byteSize(); i++) {
			System.out.printf("%02x ", b1.get(JAVA_BYTE, i) & 0xff);
		}
		System.out.println();

		// print block2 in hex
		for (int i = 0; i < b2.byteSize(); i++) {
			System.out.printf("%02x ", b2.get(JAVA_BYTE, i) & 0xff);
		}
		System.out.println();

		scratch1.fill((byte)0);
		scratch2.fill((byte)0);
		var result2 = Tester.runBlock(CPUState.filledWith(0), block2());
		System.out.println(result2);

		System.out.println(result1.equals(result2));
	}
}