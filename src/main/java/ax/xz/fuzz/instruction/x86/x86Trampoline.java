//package ax.xz.fuzz.instruction.x86;
//
//import ax.xz.fuzz.runtime.MemoryUtils;
//import ax.xz.fuzz.tester.callee_saved;
//import ax.xz.fuzz.tester.saved_state;
//import com.github.icedland.iced.x86.ICRegister;
//import com.github.icedland.iced.x86.asm.*;
//
//import java.lang.foreign.Arena;
//import java.lang.foreign.FunctionDescriptor;
//import java.lang.foreign.Linker;
//import java.lang.foreign.MemorySegment;
//import java.lang.invoke.MethodHandle;
//import java.nio.ByteBuffer;
//
//import static ax.xz.fuzz.runtime.MemoryUtils.Protection.*;
//import static ax.xz.fuzz.runtime.MemoryUtils.mmap;
//import static ax.xz.fuzz.tester.slave_h.C_POINTER;
//import static com.github.icedland.iced.x86.asm.AsmRegisters.*;
//import static java.lang.foreign.MemorySegment.NULL;
//
//public class x86Trampoline {
//	private static final FunctionDescriptor TRAMPOLINE_ENTRY = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER.withTargetLayout(saved_state.layout()));
//
//	private final Arena arena;
//	private final MemorySegment code;
//	private final MethodHandle entryHandler;
//
//	private x86Trampoline(Arena arena, MemorySegment code, MethodHandle entryHandler) {
//		this.arena = arena;
//		this.code = code;
//		this.entryHandler = entryHandler;
//	}
//
//	public static x86Trampoline createBootstrapTrampoline() {
//		var code = assembleEntryHandler(false);
//		var arena = Arena.ofAuto();
//		var codeMemory = mmap(arena, NULL, (code.length + 4095) & ~4095, READ, WRITE, EXECUTE);
//		codeMemory.fill((byte) 0xCC);
//		codeMemory.copyFrom(MemorySegment.ofArray(code));
//
//		var handle = Linker.nativeLinker().downcallHandle()
//	}
//
//	private static byte[] assembleEntryHandler(boolean withSimd) {
//		var assembler = new CodeAssembler(64);
//
//		var calleeSavedState = assembler.createLabel();
//
//		var codeAddress = assembler.createLabel();
//		var stateStructAddress = assembler.createLabel();
//
//		var tempStateStruct = assembler.createLabel();
//
//		saveABIState(assembler, calleeSavedState);
//
//		assembler.mov(qword_ptr(codeAddress), rdi);
//		assembler.mov(qword_ptr(stateStructAddress), rsi);
//		memcpy(assembler, mem_ptr(tempStateStruct), mem_ptr(rsi), callee_saved.sizeof());
//
//		loadFullState(assembler, mem_ptr(tempStateStruct), withSimd);
//		assembler.jmp(qword_ptr(codeAddress));
//		saveFullState(assembler, mem_ptr(tempStateStruct), withSimd);
//
//		assembler.mov(rsi, qword_ptr(stateStructAddress));
//		memcpy(assembler, mem_ptr(rsi), mem_ptr(tempStateStruct), callee_saved.sizeof());
//
//		loadABIState(assembler, calleeSavedState);
//		assembler.ret();
//
//		var buffer = ByteBuffer.allocate(4096);
//		assembler.assemble(buffer::put, 0);
//
//		buffer.flip();
//		var truncated = new byte[buffer.remaining()];
//		buffer.get(truncated);
//
//		return truncated;
//	}
//
//	private static void saveABIState(CodeAssembler assembler, CodeLabel calleeSavedState) {
//		assembler.mov(qword_ptr(calleeSavedState).add(callee_saved.r12$offset()), r12);
//		assembler.mov(qword_ptr(calleeSavedState).add(callee_saved.r13$offset()), r13);
//		assembler.mov(qword_ptr(calleeSavedState).add(callee_saved.r14$offset()), r14);
//		assembler.mov(qword_ptr(calleeSavedState).add(callee_saved.r15$offset()), r15);
//		assembler.mov(qword_ptr(calleeSavedState).add(callee_saved.rbp$offset()), rbp);
//		assembler.mov(qword_ptr(calleeSavedState).add(callee_saved.rsp$offset()), rsp);
//		assembler.mov(qword_ptr(calleeSavedState).add(callee_saved.rbx$offset()), rbx);
//
//		assembler.pushfq();
//		assembler.pop(rax);
//		assembler.mov(qword_ptr(calleeSavedState).add(callee_saved.rflags$offset()), rax);
//
//		assembler.stmxcsr(dword_ptr(calleeSavedState).add(callee_saved.mxcsr$offset()));
//		assembler.fnstenv(mem_ptr(calleeSavedState).add(callee_saved.fenv$offset()));
//
//		assembler.rdgsbase(rax);
//		assembler.mov(qword_ptr(calleeSavedState).add(callee_saved.gs_base$offset()), rax);
//		assembler.rdfsbase(rax);
//		assembler.mov(qword_ptr(calleeSavedState).add(callee_saved.fs_base$offset()), rax);
//
//		// mask off exceptions
//		var temp4 = assembler.createLabel();
//		var afterTemp4 = assembler.createLabel();
//
//		assembler.stmxcsr(dword_ptr(temp4));
//		assembler.or(dword_ptr(temp4), 0x1F80);
//		assembler.ldmxcsr(dword_ptr(temp4));
//		assembler.jmp(afterTemp4);
//
//		assembler.label(temp4);
//		assembler.dd(0);
//		assembler.label(afterTemp4);
//	}
//
//	private static void memcpy(CodeAssembler assembler, AsmMemoryOperand dst, AsmMemoryOperand src, long size) {
//		assembler.mov(rcx, size);
//		assembler.lea(rsi, src);
//		assembler.lea(rdi, dst);
//
//		assembler.cld();
//		assembler.rep().movsb();
//	}
//
//	private static void loadFullState(CodeAssembler assembler, AsmMemoryOperand state, boolean withSimd) {
//		if (withSimd)
//			restoreSIMDRegisters(assembler, state);
//
//		assembler.mov(rax, state.add(saved_state.fs_base$offset()));
//		assembler.wrfsbase(rax);
//		assembler.mov(rax, state.add(saved_state.gs_base$offset()));
//		assembler.wrgsbase(rax);
//
//		long[] offsets = {saved_state.rax$offset(), saved_state.rbx$offset(), saved_state.rcx$offset(), saved_state.rdx$offset(), saved_state.rsi$offset(), saved_state.rdi$offset(), saved_state.rbp$offset(), saved_state.rsp$offset(), saved_state.r8$offset(), saved_state.r9$offset(), saved_state.r10$offset(), saved_state.r11$offset(), saved_state.r12$offset(), saved_state.r13$offset(), saved_state.r14$offset(), saved_state.r15$offset()};
//		AsmRegister64[] registers = {rax, rbx, rcx, rdx, rsi, rdi, rbp, rsp, r8, r9, r10, r11, r12, r13, r14, r15};
//
//		for (int i = 0; i < offsets.length; i++) {
//			assembler.mov(registers[i], state.add(offsets[i]));
//		}
//
//		assembler.push(state.add(saved_state.rflags$offset()));
//		assembler.popfq();
//	}
//
//	private static void saveFullState(CodeAssembler assembler, AsmMemoryOperand state, boolean withSimd) {
//		assembler.pushfq();
//		assembler.pop(rax);
//		assembler.mov(state.add(saved_state.rflags$offset()), rax);
//
//		assembler.rdgsbase(rax);
//		assembler.mov(state.add(saved_state.gs_base$offset()), rax);
//		assembler.rdfsbase(rax);
//		assembler.mov(state.add(saved_state.fs_base$offset()), rax);
//
//		long[] offsets = {saved_state.rax$offset(), saved_state.rbx$offset(), saved_state.rcx$offset(), saved_state.rdx$offset(), saved_state.rsi$offset(), saved_state.rdi$offset(), saved_state.rbp$offset(), saved_state.rsp$offset(), saved_state.r8$offset(), saved_state.r9$offset(), saved_state.r10$offset(), saved_state.r11$offset(), saved_state.r12$offset(), saved_state.r13$offset(), saved_state.r14$offset(), saved_state.r15$offset()};
//		AsmRegister64[] registers = {rax, rbx, rcx, rdx, rsi, rdi, rbp, rsp, r8, r9, r10, r11, r12, r13, r14, r15};
//
//		for (int i = 0; i < offsets.length; i++) {
//			assembler.mov(state.add(offsets[i]), registers[i]);
//		}
//
//		if (withSimd)
//			saveSIMDRegisters(assembler, state);
//	}
//
//	private static void loadABIState(CodeAssembler assembler, CodeLabel calleeSavedState) {
//		assembler.mov(r12, qword_ptr(calleeSavedState).add(callee_saved.r12$offset()));
//		assembler.mov(r13, qword_ptr(calleeSavedState).add(callee_saved.r13$offset()));
//		assembler.mov(r14, qword_ptr(calleeSavedState).add(callee_saved.r14$offset()));
//		assembler.mov(r15, qword_ptr(calleeSavedState).add(callee_saved.r15$offset()));
//		assembler.mov(rbp, qword_ptr(calleeSavedState).add(callee_saved.rbp$offset()));
//		assembler.mov(rsp, qword_ptr(calleeSavedState).add(callee_saved.rsp$offset()));
//		assembler.mov(rbx, qword_ptr(calleeSavedState).add(callee_saved.rbx$offset()));
//
//		assembler.push(qword_ptr(calleeSavedState).add(callee_saved.rflags$offset()));
//		assembler.popfq();
//
//		assembler.ldmxcsr(dword_ptr(calleeSavedState).add(callee_saved.mxcsr$offset()));
//		assembler.fldenv(mem_ptr(calleeSavedState).add(callee_saved.fenv$offset()));
//
//		assembler.mov(rax, qword_ptr(calleeSavedState).add(callee_saved.gs_base$offset()));
//		assembler.wrgsbase(rax);
//		assembler.mov(rax, qword_ptr(calleeSavedState).add(callee_saved.fs_base$offset()));
//		assembler.wrfsbase(rax);
//	}
//
//	private static void restoreSIMDRegisters(CodeAssembler assembler, AsmMemoryOperand state) {
//		var arch = X86Architecture.ofNative();
//		var sseRegisters = state.displacement(saved_state.zmm$offset());
//		var mmxRegisters = state.displacement(saved_state.mm$offset());
//
//		if (arch.supportsAVX512()) {
//			restoreAvx512(assembler, sseRegisters);
//		} else if (arch.supportsAVX()) {
//			restoreAvx(assembler, sseRegisters);
//		} else if (arch.supportsSSE()) {
//			restoreSSE(assembler, arch, sseRegisters);
//		}
//
//		if (arch.supportsMMX()) {
//			restoreMMX(assembler, mmxRegisters);
//		}
//	}
//
//	private static void saveSIMDRegisters(CodeAssembler assembler, AsmMemoryOperand state) {
//		var arch = X86Architecture.ofNative();
//		var sseRegisters = (state).displacement(saved_state.zmm$offset());
//		var mmxRegisters = (state).displacement(saved_state.mm$offset());
//
//		if (arch.supportsAVX512()) {
//			saveAvx512(assembler, sseRegisters);
//		} else if (arch.supportsAVX()) {
//			saveAvx(assembler, sseRegisters);
//		} else if (arch.supportsSSE()) {
//			saveSSE(assembler, arch, sseRegisters);
//		}
//
//		if (arch.supportsMMX()) {
//			saveMMX(assembler, mmxRegisters);
//		}
//	}
//
//	private static void restoreAvx512(CodeAssembler assembler, AsmMemoryOperand sseRegisters) {
//		for (int i = 0; i < 32; i++) {
//			assembler.vmovups(new AsmRegisterZMM(new ICRegister(zmm0.get().get() + i)), sseRegisters.add(i * 64L));
//		}
//	}
//
//	private static void restoreAvx(CodeAssembler assembler, AsmMemoryOperand sseRegisters) {
//		for (int i = 0; i < 16; i++) {
//			var target = new AsmRegisterYMM(new ICRegister(ymm0.get().get() + i));
//			assembler.vmovups(target, sseRegisters.add(i * 64L));
//		}
//	}
//
//	private static void restoreSSE(CodeAssembler assembler, X86Architecture arch, AsmMemoryOperand sseRegisters) {
//		for (int i = 0; i < (arch.supportsSSE2() ? 16 : 8); i++) {
//			var target = new AsmRegisterXMM(new ICRegister(xmm0.get().get() + i));
//			assembler.movups(target, sseRegisters.add(i * 64L));
//		}
//	}
//
//	private static void restoreMMX(CodeAssembler assembler, AsmMemoryOperand mmxRegisters) {
//		for (int i = 0; i < 8; i++) {
//			var target = new AsmRegisterMM(new ICRegister(mm0.get().get() + i));
//			assembler.movq(target, mmxRegisters.add(i * 8L));
//		}
//	}
//
//	private static void saveAvx512(CodeAssembler assembler, AsmMemoryOperand sseRegisters) {
//		for (int i = 0; i < 32; i++) {
//			assembler.vmovups(sseRegisters.add(i * 64L), new AsmRegisterZMM(new ICRegister(zmm0.get().get() + i)));
//		}
//	}
//
//	private static void saveAvx(CodeAssembler assembler, AsmMemoryOperand sseRegisters) {
//		for (int i = 0; i < 16; i++) {
//			var target = new AsmRegisterYMM(new ICRegister(ymm0.get().get() + i));
//			assembler.vmovups(sseRegisters.add(i * 64L), target);
//		}
//	}
//
//	private static void saveSSE(CodeAssembler assembler, X86Architecture arch, AsmMemoryOperand sseRegisters) {
//		for (int i = 0; i < (arch.supportsSSE2() ? 16 : 8); i++) {
//			var target = new AsmRegisterXMM(new ICRegister(xmm0.get().get() + i));
//			assembler.movups(sseRegisters.add(i * 64L), target);
//		}
//	}
//
//	private static void saveMMX(CodeAssembler assembler, AsmMemoryOperand mmxRegisters) {
//		for (int i = 0; i < 8; i++) {
//			var target = new AsmRegisterMM(new ICRegister(mm0.get().get() + i));
//			assembler.movq(mmxRegisters.add(i * 8L), target);
//		}
//	}
//}
