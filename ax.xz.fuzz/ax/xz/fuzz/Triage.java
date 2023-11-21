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
 * 0:
 * btc cx,cx
 * blendps xmm8,xmm9,90h
 * paddq xmm5,xmm2
 * vpunpckhwd ymm13,ymm14,ymm14
 * psrld mm6,0B9h
 * mulsd xmm12,xmm10
 * sets dil
 * vfnmsub231ps xmm2,xmm14,xmm14
 * sha1rnds4 xmm13,xmm5,40h
 * vpextrq r9,xmm14,0DBh
 * pinsrw mm2,rdx,94h
 * packssdw mm5,mm1
 * vphaddd xmm7,xmm11,xmm0
 * crc32 r8d,r12d
 * setle ch
 * xgetbv
 * jnb 2
 * jmp 1
 *
 * 1:
 * vroundss xmm2,xmm13,xmm5,96h
 * vcvttps2dq xmm2,xmm2
 * dec bl
 * vpaddsb ymm12,ymm4,ymm10
 * test dl,r12b
 * addsubps xmm2,xmm5
 * vpermilps xmm8,xmm14,xmm5
 * extrq xmm10,0B8h,6Ah
 * vcvtpd2dq xmm14,xmm8
 * sbb r12w,54h
 * vmovaps xmm7,xmm4
 * vmaxps xmm2,xmm14,xmm14
 * pand mm0,mm6
 * movsx r11,bp
 * vfnmadd213pd ymm7,ymm7,ymm7
 * pcmpgtq xmm3,xmm11
 * pblendw xmm10,xmm7,0AEh
 * je 1
 * jmp 1
 */
	var zero = assembler.createLabel();
	var one = assembler.createLabel();


	assembler.label(zero);
	assembler.btc(cx, cx);
	assembler.blendps(xmm8, xmm9, 0x90);
	assembler.paddq(xmm5, xmm2);
	assembler.vpunpckhwd(ymm13, ymm14, ymm14);
	assembler.psrld(mm6, 0xB9);
	assembler.mulsd(xmm12, xmm10);
	assembler.sets(dil);
	assembler.vfnmsub231ps(xmm2, xmm14, xmm14);
	assembler.sha1rnds4(xmm13, xmm5, 0x40);
	assembler.vpextrq(r9, xmm14, 0xDB);
	assembler.pinsrw(mm2, rdx, 0x94);
	assembler.packssdw(mm5, mm1);
	assembler.vphaddd(xmm7, xmm11, xmm0);
	assembler.crc32(r8d, r12d);
	assembler.setle(ch);
	assembler.xgetbv();

	assembler.label(one);
	assembler.vroundss(xmm2, xmm13, xmm5, 0x96);
	assembler.vcvttps2dq(xmm2, xmm2);
	assembler.dec(bl);
	assembler.vpaddsb(ymm12, ymm4, ymm10);
	assembler.test(dl, r12b);
	assembler.addsubps(xmm2, xmm5);
	assembler.vpermilps(xmm8, xmm14, xmm5);
	assembler.extrq(xmm10, 0xB8, 0x6A);
	assembler.vcvtpd2dq(xmm14, xmm8);
	assembler.sbb(r12w, 0x54);
	assembler.vmovaps(xmm7, xmm4);
	assembler.vmaxps(xmm2, xmm14, xmm14);
	assembler.pand(mm0, mm6);
	assembler.movsx(r11, bp);
	assembler.vfnmadd213pd(ymm7, ymm7, ymm7);
	assembler.pcmpgtq(xmm3, xmm11);
	assembler.pblendw(xmm10, xmm7, 0xAE);
	assembler.je(one);
		assembler.jnb(TestCase.TEST_CASE_FINISH.address());

		var buf = seg.asByteBuffer();
		assembler.assemble(buf::put, 0);
		return seg.asSlice(0, buf.position());
	}

	public static MemorySegment block2() {
		var seg = mmap(MemorySegment.NULL, 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(), MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0)
				.reinterpret(4096, Arena.ofAuto(), ms -> munmap(ms, 4096));

		var assembler = new CodeAssembler(64);

		/**
		 * 0:
		 * btc cx,cx
		 * mulsd xmm12,xmm10
		 * blendps xmm8,xmm9,90h
		 * sets dil
		 * paddq xmm5,xmm2
		 * vpunpckhwd ymm13,ymm14,ymm14
		 * pinsrw mm2,rdx,94h
		 * packssdw mm5,mm1
		 * vphaddd xmm7,xmm11,xmm0
		 * crc32 r8d,r12d
		 * setle ch
		 * psrld mm6,0B9h
		 * vfnmsub231ps xmm2,xmm14,xmm14
		 * sha1rnds4 xmm13,xmm5,40h
		 * xgetbv
		 * vpextrq r9,xmm14,0DBh
		 * jnb 2
		 * jmp 1
		 *
		 * 1:
		 * dec bl
		 * vpaddsb ymm12,ymm4,ymm10
		 * vroundss xmm2,xmm13,xmm5,96h
		 * vcvttps2dq xmm2,xmm2
		 * test dl,r12b
		 * extrq xmm10,0B8h,6Ah
		 * addsubps xmm2,xmm5
		 * vpermilps xmm8,xmm14,xmm5
		 * vcvtpd2dq xmm14,xmm8
		 * sbb r12w,54h
		 * vmaxps xmm2,xmm14,xmm14
		 * vmovaps xmm7,xmm4
		 * vfnmadd213pd ymm7,ymm7,ymm7
		 * pcmpgtq xmm3,xmm11
		 * pand mm0,mm6
		 * movsx r11,bp
		 * pblendw xmm10,xmm7,0AEh
		 * je 1
		 * jmp 1
		 */
		var zero = assembler.createLabel();
		var one = assembler.createLabel();

		assembler.label(zero);
		assembler.btc(cx, cx);
		assembler.mulsd(xmm12, xmm10);
		assembler.blendps(xmm8, xmm9, 0x90);
		assembler.sets(dil);
		assembler.paddq(xmm5, xmm2);
		assembler.vpunpckhwd(ymm13, ymm14, ymm14);
		assembler.pinsrw(mm2, rdx, 0x94);
		assembler.packssdw(mm5, mm1);
		assembler.vphaddd(xmm7, xmm11, xmm0);
		assembler.crc32(r8d, r12d);
		assembler.setle(ch);
		assembler.psrld(mm6, 0xB9);
		assembler.vfnmsub231ps(xmm2, xmm14, xmm14);
		assembler.sha1rnds4(xmm13, xmm5, 0x40);
		assembler.xgetbv();
		assembler.vpextrq(r9, xmm14, 0xDB);
		assembler.jnb(one);

		assembler.label(one);
		assembler.dec(bl);
		assembler.vpaddsb(ymm12, ymm4, ymm10);
		assembler.vroundss(xmm2, xmm13, xmm5, 0x96);
		assembler.vcvttps2dq(xmm2, xmm2);
		assembler.test(dl, r12b);
		assembler.extrq(xmm10, 0xB8, 0x6A);
		assembler.addsubps(xmm2, xmm5);
		assembler.vpermilps(xmm8, xmm14, xmm5);
		assembler.vcvtpd2dq(xmm14, xmm8);
		assembler.sbb(r12w, 0x54);
		assembler.vmaxps(xmm2, xmm14, xmm14);
		assembler.vmovaps(xmm7, xmm4);
		assembler.vfnmadd213pd(ymm7, ymm7, ymm7);
		assembler.pcmpgtq(xmm3, xmm11);
		assembler.pand(mm0, mm6);
		assembler.movsx(r11, bp);
		assembler.pblendw(xmm10, xmm7, 0xAE);
		assembler.je(one);
		assembler.jnb(TestCase.TEST_CASE_FINISH.address());


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