package ax.xz.fuzz.x86.runtime;

import com.github.icedland.iced.x86.asm.CodeAssembler;
import com.github.icedland.iced.x86.asm.CodeLabel;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static ax.xz.fuzz.mman.MemoryUtils.Protection.*;
import static ax.xz.fuzz.mman.MemoryUtils.mmap;
import static com.github.icedland.iced.x86.asm.AsmRegisters.*;
import static java.lang.foreign.MemoryLayout.sequenceLayout;
import static java.lang.foreign.MemorySegment.NULL;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public class Cpuid {
	private static final MethodHandle cpuidCheckFunction = assembleCpuidStub();
	private static final Map<CpuidCall, CpuidResult> cache = new ConcurrentHashMap<>();

	static {
		X86LibraryLoader.load();
	}

	public static CpuidResult cpuid(int leaf, int subleaf) {
		var call = new CpuidCall(leaf, subleaf);
		return cache.computeIfAbsent(call, k -> getCpuid0(k.leaf(), k.subleaf()));
	}

	private static CpuidResult getCpuid0(int leaf, int subleaf) {
		try (var arena = Arena.ofConfined()) {
			var result = arena.allocate(16);
			cpuidCheckFunction.invokeExact(result, leaf, subleaf);

			var arr = result.toArray(JAVA_INT);
			return new CpuidResult(arr[0], arr[1], arr[2], arr[3]);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	private static MethodHandle assembleCpuidStub() {
		var code = mmap(Arena.ofAuto(), NULL, 4096, READ, WRITE, EXECUTE);
		code.fill((byte) 0xcc);

		var assembler = new CodeAssembler(64);

		assembler.mov(rax, rsi);
		assembler.mov(rcx, rdx);
		assembler.cpuid();

		assembler.mov(dword_ptr(rdi), eax);
		assembler.mov(dword_ptr(rdi, 4), ebx);
		assembler.mov(dword_ptr(rdi, 8), ecx);
		assembler.mov(dword_ptr(rdi, 12), edx);

		assembler.ret();

		assembler.assemble(code.asByteBuffer()::put, code.address());
		return Linker.nativeLinker().downcallHandle(code, FunctionDescriptor.ofVoid(
			ADDRESS, JAVA_INT, JAVA_INT
		));
	}

	private record CpuidCall(int leaf, int subleaf) {
	}

	public record CpuidResult(long eax, long ebx, long ecx, long edx) {
	}
}
