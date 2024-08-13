package ax.xz.fuzz.x86.arch;

import ax.xz.fuzz.arch.CPUState;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.runtime.ExecutionResult;
import ax.xz.fuzz.x86.runtime.X86TestExecutor;
import com.github.icedland.iced.x86.asm.CodeAssembler;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

import static ax.xz.fuzz.mman.MemoryUtils.Protection.*;
import static ax.xz.fuzz.mman.MemoryUtils.mmap;
import static ax.xz.fuzz.x86.tester.slave_h.maybe_allocate_signal_stack;
import static ax.xz.fuzz.x86.tester.slave_h.trampoline_return_address;
import static com.github.icedland.iced.x86.asm.AsmRegisters.*;
import static com.github.icedland.iced.x86.asm.AsmRegistersZMM.zmm16;
import static java.lang.foreign.MemorySegment.NULL;

public record X86UarchInfo(long supportedExtensions) {
	public static final long SSE = 1 << 1;
	public static final long SSE2 = 1 << 2;
	public static final long SSE3 = 1 << 3;
	public static final long SSE4 = 1 << 4;
	public static final long AVX = 1 << 5;
	public static final long AVX2 = 1 << 6;
	public static final long AVX512 = 1 << 7;
	public static final long MMX = 1 << 8;

	public boolean supportsMMX() {
		return (supportedExtensions & X86UarchInfo.MMX) != 0;
	}

	public boolean supportsSSE() {
		return (supportedExtensions & X86UarchInfo.SSE) != 0;
	}

	public boolean supportsSSE2() {
		return (supportedExtensions & X86UarchInfo.SSE2) != 0;
	}

	public boolean supportsAVX() {
		return (supportedExtensions & X86UarchInfo.AVX) != 0;
	}

	public boolean supportsAVX2() {
		return (supportedExtensions & X86UarchInfo.AVX2) != 0;
	}

	public boolean supportsAVX512() {
		return (supportedExtensions & X86UarchInfo.AVX512) != 0;
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();

		sb.append("Supported extensions: ");
		if (supportsMMX()) sb.append("MMX ");
		if (supportsSSE()) sb.append("SSE ");
		if (supportsSSE2()) sb.append("SSE2 ");
		if (supportsAVX()) sb.append("AVX ");
		if (supportsAVX2()) sb.append("AVX2 ");
		if (supportsAVX512()) sb.append("AVX512 ");

		return sb.toString();
	}

	public static X86UarchInfo loadNativeInfo() {
		long extensions = 0;

		if (checkInstructionExistence(asm -> asm.movss(xmm0, xmm0))) extensions |= SSE;
		if (checkInstructionExistence(asm -> asm.orpd(xmm8, xmm8))) extensions |= SSE2;
		if (checkInstructionExistence(asm -> asm.addsubpd(xmm0, xmm0)))
			extensions |= SSE3;

		if (checkInstructionExistence(asm -> asm.vmovups(ymm0, ymm0)))
			extensions |= AVX;
		if (checkInstructionExistence(asm -> asm.vpbroadcastb(xmm0, xmm0)))
			extensions |= AVX2;
		if (checkInstructionExistence(asm -> asm.vmovups(zmm16, zmm16)))
			extensions |= AVX512;

		if (checkInstructionExistence(asm -> asm.movq(mm0, mm0))) extensions |= MMX;

		return new X86UarchInfo(extensions);
	}

	public static boolean checkInstructionExistence(Consumer<CodeAssembler> assemblerConsumer) {
		try (var arena = Arena.ofConfined()) {
			var code = mmap(arena, NULL, 4096, READ, WRITE, EXECUTE);
			code.fill((byte) 0xcc);

			var assembler = new CodeAssembler(64);
			assemblerConsumer.accept(assembler);
			assembler.jmp(trampoline_return_address().address());

			var buf = code.asByteBuffer();
			assembler.assemble(buf::put, 0);
			buf.flip();
			var bufSeg = MemorySegment.ofBuffer(buf);
			code = code.asSlice(0, bufSeg.byteSize());
			code.copyFrom(bufSeg);

			var result = X86TestExecutor.runCode(RegisterSet.EMPTY, code, CPUState.zeroed());
			return !(result instanceof ExecutionResult.Fault.Sigill);
		}
	}
}
