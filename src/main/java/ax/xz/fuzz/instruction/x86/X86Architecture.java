package ax.xz.fuzz.instruction.x86;

import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.runtime.Architecture;
import ax.xz.fuzz.runtime.ExecutionResult;
import ax.xz.fuzz.runtime.SigillReason;
import ax.xz.fuzz.runtime.state.CPUState;
import ax.xz.fuzz.tester.execution_result;
import ax.xz.fuzz.tester.fault_details;
import ax.xz.fuzz.tester.saved_state;
import com.github.icedland.iced.x86.asm.CodeAssembler;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Consumer;

import static ax.xz.fuzz.instruction.x86.x86RegisterDescriptor.*;
import static ax.xz.fuzz.runtime.MemoryUtils.Protection.*;
import static ax.xz.fuzz.runtime.MemoryUtils.mmap;
import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.asm.AsmRegisters.*;
import static com.github.icedland.iced.x86.asm.AsmRegistersZMM.zmm16;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.MemorySegment.NULL;

public class X86Architecture implements Architecture {
	public static final long SSE = 1 << 1;
	public static final long SSE2 = 1 << 2;
	public static final long SSE3 = 1 << 3;
	public static final long SSE4 = 1 << 4;
	public static final long AVX = 1 << 5;
	public static final long AVX2 = 1 << 6;
	public static final long AVX512 = 1 << 7;
	public static final long MMX = 1 << 8;
	private static X86Architecture INSTANCE = null;
	private final long supportedExtensions;
	private final x86RegisterDescriptor[] descriptors;
	private final Set<x86RegisterDescriptor> descriptorSet;
	private final RegisterSet SAVED = RegisterSet.of(FS, GS, RFLAGS,
		RAX, RBX, RCX, RDX, RSI, RDI, RBP, R8, R9, R10, R11, R12, R13, R14, R15, RSP,
		ZMM0, ZMM1, ZMM2, ZMM3, ZMM4, ZMM5, ZMM6, ZMM7, ZMM8, ZMM9, ZMM10, ZMM11, ZMM12, ZMM13, ZMM14, ZMM15,
		ZMM16, ZMM17, ZMM18, ZMM19, ZMM20, ZMM21, ZMM22, ZMM23, ZMM24, ZMM25, ZMM26, ZMM27, ZMM28, ZMM29, ZMM30, ZMM31,
		MM0, MM1, MM2, MM3, MM4, MM5, MM6, MM7
	);

	X86Architecture(long supportedExtensions, x86RegisterDescriptor[] descriptors) {
		this.supportedExtensions = supportedExtensions;
		this.descriptors = descriptors;
		this.descriptorSet = Set.of(descriptors);
	}

	@Override
	public x86RegisterDescriptor registerByIndex(int index) {
		return descriptors[index];
	}

	public RegisterSet trackedRegisters() {
		return (supportsAVX512() ? x86RegisterBanks.ALL_AVX512 : x86RegisterBanks.ALL_AVX2);
	}

	@Override
	public RegisterSet validRegisters() {
		return supportsAVX512() ? x86RegisterBanks.ALL_AVX512 : x86RegisterBanks.ALL_AVX2;
	}

	public boolean supportsAVX512() {
		return (supportedExtensions & AVX512) != 0;
	}

	@Override
	public RegisterDescriptor defaultCounter() {
		return R15;
	}

	@Override
	public x86RegisterDescriptor stackPointer() {
		return RSP;
	}

	@Override
	public ExecutionResult runSegment(MemorySegment code, CPUState initialState) {
		try (var arena = Arena.ofConfined()) {
			var result = execution_result.allocate(arena);
			var state = execution_result.state(result);

			for (var entry : initialState.values().entrySet()) {
				if (!SAVED.hasRegister(entry.getKey()))
					continue;

				var descriptor = (x86RegisterDescriptor) entry.getKey();
				var value = entry.getValue();

				var offset = stateStructOffset(descriptor);
				state.asSlice(offset, descriptor.widthBytes()).copyFrom(MemorySegment.ofArray(value));
			}

			do_test(code, code.byteSize(), result);

			if (execution_result.faulted(result))
				return ofFaultDetails(execution_result.state(result));

			var map = new HashMap<RegisterDescriptor, byte[]>();

			for (var descriptor : SAVED) {
				var offset = stateStructOffset((x86RegisterDescriptor) descriptor);
				var value = new byte[descriptor.widthBytes()];
				MemorySegment.ofArray(value).copyFrom(state.asSlice(offset, descriptor.widthBytes()));
				map.put(descriptor, value);
			}

			return new ExecutionResult.Success(new CPUState(map));
		}
	}

	@Override
	public boolean interestingMismatch(ExecutionResult a, ExecutionResult b) {
		if (a instanceof ExecutionResult.Success(CPUState(var A)) && b instanceof ExecutionResult.Success(
			CPUState(var B)
		)) {
			for (var l : A.keySet()) {
				if (((x86RegisterDescriptor) l).bank() != x86RegisterBank.SPECIAL
				    && !Arrays.equals(A.get(l), B.get(l))) {
					return true;
				}
			}
		}

		return false;
	}

	private long stateStructOffset(x86RegisterDescriptor descriptor) {
		return switch (descriptor.bank()) {
			case x86RegisterBank _ when descriptor == FS -> saved_state.fs_base$offset();
			case x86RegisterBank _ when descriptor == GS -> saved_state.gs_base$offset();
			case x86RegisterBank _ when descriptor == RFLAGS -> saved_state.rflags$offset();

			case GPRQ -> saved_state.layout().byteOffset(groupElement(descriptor.name().toLowerCase()));
			case LOWER_ZMM -> saved_state.zmm$offset() + 64L * descriptor.indexWithinBank();
			case UPPER_ZMM -> saved_state.zmm$offset() + 64L * (descriptor.indexWithinBank() + 16);
			case MMX -> saved_state.mm$offset() + 8L * descriptor.indexWithinBank();

			default -> throw new IllegalStateException("Register not in saved state: " + descriptor);
		};
	}

	private static ExecutionResult.Fault ofFaultDetails(MemorySegment faultDetails) {

		return switch (fault_details.fault_reason(faultDetails)) {
			case 11 ->
				new ExecutionResult.Fault.Sigsegv(fault_details.fault_address(faultDetails).address());
			case 4 ->
				new ExecutionResult.Fault.Sigill(fault_details.fault_address(faultDetails).address(), SigillReason.fromOsValue(fault_details.fault_code(faultDetails)));
			case 7 -> new ExecutionResult.Fault.Sigbus(fault_details.fault_address(faultDetails).address());
			case 8 -> new ExecutionResult.Fault.Sigfpe(fault_details.fault_address(faultDetails).address());
			case 5 ->
				new ExecutionResult.Fault.Sigtrap(fault_details.fault_address(faultDetails).address());
			case 14 ->
				new ExecutionResult.Fault.Sigalrm(fault_details.fault_address(faultDetails).address());
			default ->
				new ExecutionResult.Fault.Unknown(fault_details.fault_address(faultDetails).address(), fault_details.fault_reason(faultDetails));
		};
	}

	public boolean supportsAVX2() {
		return (supportedExtensions & AVX2) != 0;
	}

	public boolean supportsAVX() {
		return (supportedExtensions & AVX) != 0;
	}

	public boolean supportsSSE() {
		return (supportedExtensions & SSE) != 0;
	}

	public boolean supportsSSE2() {
		return (supportedExtensions & SSE2) != 0;
	}

	public boolean supportsMMX() {
		return (supportedExtensions & MMX) != 0;
	}

	public long supportedExtensions() {
		return supportedExtensions;
	}

	public static X86Architecture ofNative() {
		if (INSTANCE == null) {
			synchronized (X86Architecture.class) {
				if (INSTANCE == null) {
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

					boolean supportsAvx512 = (extensions & AVX512) != 0;

					var descriptors = Arrays.stream(x86RegisterDescriptor.values()).filter(n -> !n.requiresEvex() || supportsAvx512)
						.toArray(x86RegisterDescriptor[]::new);

					INSTANCE = new X86Architecture(extensions, descriptors);
				}
			}
		}

		return INSTANCE;
	}

	public static boolean checkInstructionExistence(Consumer<CodeAssembler> assemblerConsumer) {
		boolean supportsEvex;
		try (var arena = Arena.ofConfined()) {
			maybe_allocate_signal_stack();
			var code = mmap(arena, NULL, 4096, READ, WRITE, EXECUTE);
			code.fill((byte) 0xcc);

			var assembler = new CodeAssembler(64);
			assemblerConsumer.accept(assembler);
			assembler.jmp(trampoline_return_address().address());

			var buf = code.asByteBuffer();
			assembler.assemble(buf::put, 0);
			buf.flip();
			code.copyFrom(MemorySegment.ofBuffer(buf));

			var result = execution_result.allocate(arena);
			do_test(code, 15, result);


			supportsEvex = !execution_result.faulted(result) || fault_details.fault_reason(execution_result.fault(result)) != 4;
		}
		return supportsEvex;
	}
}
