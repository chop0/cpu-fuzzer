package ax.xz.fuzz;

import com.github.icedland.iced.x86.asm.CodeAssembler;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.asm.AsmRegisters.*;

public class Triage {
	private static MemorySegment block1() {
		var seg = mmap(MemorySegment.NULL, 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(), MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0)
				.reinterpret(4096, Arena.ofAuto(), ms -> munmap(ms, 4096));
		var assembler = new CodeAssembler(64);

		/*
		psllw xmm2,xmm8
vcvtps2pd ymm14,xmm8
comiss xmm9,xmm8
vfnmadd231ss xmm11,xmm5,xmm1
phsubw mm1,mm2
pcmpgtw mm6,mm6
vpermps ymm10,ymm0,ymm4
blsr r13,r10
vzeroall
pmullw xmm10,xmm4
adox r11,r13
cmovnp r11d,ebx
pinsrw mm1,r12,9Eh
vdivpd ymm4,ymm11,ymm1
		 */
		assembler.vzeroall();
		assembler.pmullw(xmm10,xmm4);
		assembler.vdivpd(ymm4,ymm11,ymm1);
		assembler.jmp(TestCase.TEST_CASE_FINISH.address());

		var buf = seg.asByteBuffer();
		assembler.assemble(buf::put, 0);
		return seg.asSlice(0, buf.position());
	}

	public static MemorySegment block2() {
		var seg = mmap(MemorySegment.NULL, 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(), MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0)
				.reinterpret(4096, Arena.ofAuto(), ms -> munmap(ms, 4096));

		var assembler = new CodeAssembler(64);

		/*
		psllw xmm2,xmm8
vfnmadd231ss xmm11,xmm5,xmm1
phsubw mm1,mm2
vcvtps2pd ymm14,xmm8
vpermps ymm10,ymm0,ymm4
pmullw xmm10,xmm4
pinsrw mm1,r12,9Eh
comiss xmm9,xmm8
pcmpgtw mm6,mm6
vdivpd ymm4,ymm11,ymm1
blsr r13,r10
vzeroall
adox r11,r13
cmovnp r11d,ebx
		 */
		assembler.pmullw(xmm10,xmm4);
		assembler.vdivpd(ymm4,ymm11,ymm1);
		assembler.vzeroall();
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

		scratch1.fill((byte)0);
		scratch2.fill((byte)0);
		var result2 = Tester.runBlock(CPUState.filledWith(0), block2());
		System.out.println(result2);

		System.out.println(result1.equals(result2));
	}
}