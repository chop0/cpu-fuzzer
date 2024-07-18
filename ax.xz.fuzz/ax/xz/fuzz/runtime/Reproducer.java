package ax.xz.fuzz.runtime;

import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.dec.ByteArrayCodeReader;
import com.github.icedland.iced.x86.dec.Decoder;
import com.github.icedland.iced.x86.fmt.StringOutput;
import com.github.icedland.iced.x86.fmt.gas.GasFormatter;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;

import static ax.xz.fuzz.runtime.MemoryUtils.alignUp;
import static com.github.icedland.iced.x86.Register.R15;


public class Reproducer {
	private static final MemorySegment TRAMPOLINE_ADDRESS = MemorySegment.ofAddress(0x4000_0000).reinterpret(Trampoline.byteSize());
	private static final MemorySegment CODE1_ADDRESS = MemorySegment.ofAddress(0x5000_0000).reinterpret(4096);
	private static final MemorySegment CODE2_ADDRESS = MemorySegment.ofAddress(0x6000_0000).reinterpret(4096);

	private static String getAssembly(ExecutableSequence executableSequence, long startRip) {
		byte[] encoded = new byte[4096];
		int size = executableSequence.encode(startRip, new Trampoline(TRAMPOLINE_ADDRESS), MemorySegment.ofArray(encoded), R15, 100);

		var decoder = new Decoder(64, new ByteArrayCodeReader(encoded), startRip);
		var instructions = new ArrayList<Instruction>();

		while (decoder.getIP() < (startRip + size)) {
			var instruction = decoder.decode();
			instructions.add(instruction);
		}

		var formatter = new GasFormatter();
		var result = new StringBuilder();

		for (var instruction : instructions) {
			var buf = new StringOutput();
			formatter.format(instruction, buf);

			var line = buf.toString();

			if (!line.contains(":"))
				result.append("\t");
			else
				result.append("\n");

			result.append(line).append("\n");
		}

		return result.toString();
	}

	public static String createReproducer(ExecutableSequence a, ExecutableSequence b) {
		var size = alignUp(TRAMPOLINE_ADDRESS.byteSize(), 4096);
		return """
%s

.globl main
main:
	# create trampoline
	# mmap($0x%1$lx, $0x%2$lx, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0)
	movq $0x%lx, %%rdi
	movq $0x%lx, %%rsi
	movq $7, %%rdx
	movq $0x22, %%r10
	movq $-1, %%r8
	movq $0, %%r9
	mov $9, %%rax
	syscall
	
	cmpq $-1, %%rax
	leaq mmap_text(%%rip), %%rdi
	je .perror
	
	# copy trampoline code
	leaq routine_begin(%%rip), %%rsi
	leaq routine_end(%%rip), %%rcx
	subq %%rsi, %%rcx
	movq $0x%1$lx, %%rdi
	rep movsb
	
	jmp
""";
	}

}
