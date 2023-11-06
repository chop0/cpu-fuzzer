package ax.xz.fuzz;

import com.github.icedland.iced.x86.asm.CodeAssembler;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.asm.AsmRegisters.*;

public class Triage {
	private static MemorySegment block1() {
		var seg = Arena.ofAuto().allocate(4096);
		var assembler = new CodeAssembler(64);

		assembler.psrad(xmm7,xmm9);
		assembler.mov(r11w,0x273A);
		assembler.psraw(xmm5,0x2D);
		assembler.pslld(mm1,mm4);
		assembler.sfence();
		assembler.rdpid(rsp);
		assembler.mfence();
		assembler.prefetchnta(mem_ptr	(0x10000870));
		assembler.pshufw(mm7,mm7,0x0EF);

		var buf = seg.asByteBuffer();
		assembler.assemble(buf::put, 0);
		return seg.asSlice(0, buf.position());
	}

	public static MemorySegment block2() {
		var seg = Arena.ofAuto().allocate(4096);
		var assembler = new CodeAssembler(64);

		assembler.mov(r11w,0x273A);
		assembler.sfence();
		assembler.psrad(xmm7,xmm9);
		assembler.psraw(xmm5,0x2D);
		assembler.pslld(mm1,mm4);
		assembler.rdpid(rsp);
		assembler.mfence();
		assembler.prefetchnta(mem_ptr(0x10000870));
		assembler.pshufw(mm7,mm7,0x0EF);

		var buf = seg.asByteBuffer();
		assembler.assemble(buf::put, 0);
		return seg.asSlice(0, buf.position());
	}


	public static void main(String[] args) throws InstructionGenerator.NoPossibilitiesException, CombinedBlock.UnencodeableException {
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