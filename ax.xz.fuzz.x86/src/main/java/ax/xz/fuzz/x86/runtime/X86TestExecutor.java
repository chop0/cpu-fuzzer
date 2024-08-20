package ax.xz.fuzz.x86.runtime;

import ax.xz.fuzz.arch.CPUState;
import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.runtime.ExecutionResult;
import ax.xz.fuzz.runtime.SigillReason;
import ax.xz.fuzz.x86.tester.execution_result;
import ax.xz.fuzz.x86.tester.fault_details;
import ax.xz.fuzz.x86.tester.saved_state;
import ax.xz.fuzz.x86.arch.x86RegisterBank;
import ax.xz.fuzz.x86.arch.x86RegisterDescriptor;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.HashMap;

import static ax.xz.fuzz.x86.tester.slave_h.*;
import static ax.xz.fuzz.x86.arch.x86RegisterDescriptor.*;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;

public class X86TestExecutor {
	public static ExecutionResult runCode(RegisterSet trackedRegisters, MemorySegment code, CPUState initialState) {
		try (var arena = Arena.ofConfined()) {
			var result = execution_result.allocate(arena);
			var state = execution_result.state(result);

			storeState(trackedRegisters, initialState, state);

			do_test(code, code.byteSize(), result);

			if (execution_result.faulted(result))
				return loadFaultDetails(execution_result.state(result));

			var map = loadState(trackedRegisters, state);

			return new ExecutionResult.Success(new CPUState(map));
		}
	}

	private static ExecutionResult.Fault loadFaultDetails(MemorySegment faultDetails) {
		return switch (fault_details.fault_reason(faultDetails)) {
			case 11 ->
				new ExecutionResult.Fault.Sigsegv(fault_details.fault_address(faultDetails).address());
			case 4 ->
				new ExecutionResult.Fault.Sigill(fault_details.fault_address(faultDetails).address(), loadSigillReason(fault_details.fault_code(faultDetails)));
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

	private static SigillReason loadSigillReason(int faultCode) {
		if (faultCode == ILL_ILLOPC())
			return SigillReason.ILL_ILLOPC;
		if (faultCode == ILL_ILLOPN())
			return SigillReason.ILL_ILLOPN;
		if (faultCode == ILL_ILLADR())
			return SigillReason.ILL_ILLADR;
		if (faultCode == ILL_ILLTRP())
			return SigillReason.ILL_ILLTRP;
		if (faultCode == ILL_PRVOPC())
			return SigillReason.ILL_PRVOPC;
		if (faultCode == ILL_PRVREG())
			return SigillReason.ILL_PRVREG;
		if (faultCode == ILL_COPROC())
			return SigillReason.ILL_COPROC;
		if (faultCode == ILL_BADSTK())
			return SigillReason.ILL_BADSTK;

		return null;
	}

	private static void storeState(RegisterSet descriptors, CPUState initialState, MemorySegment state) {
		for (var entry : initialState.values().entrySet()) {
			if (!descriptors.hasRegister(entry.getKey())) // TODO: store and load mask registers
				continue;

			var descriptor = (x86RegisterDescriptor) entry.getKey();
			var value = entry.getValue();

			var offset = stateStructOffset(descriptor);
			state.asSlice(offset, descriptor.widthBytes()).copyFrom(MemorySegment.ofArray(value));
		}
	}

	private static HashMap<RegisterDescriptor, byte[]> loadState(RegisterSet descriptors, MemorySegment state) {
		var map = new HashMap<RegisterDescriptor, byte[]>();

		for (var descriptor : descriptors) {
			var offset = stateStructOffset((x86RegisterDescriptor) descriptor);
			var value = new byte[descriptor.widthBytes()];
			MemorySegment.ofArray(value).copyFrom(state.asSlice(offset, descriptor.widthBytes()));
			map.put(descriptor, value);
		}
		return map;
	}

	private static long stateStructOffset(x86RegisterDescriptor descriptor) {
		return switch (descriptor.bank()) {
			case x86RegisterBank _ when descriptor == FS -> saved_state.fs_base$offset();
			case x86RegisterBank _ when descriptor == GS -> saved_state.gs_base$offset();
			case x86RegisterBank _ when descriptor == RFLAGS -> saved_state.rflags$offset();

			case GPRQ -> saved_state.layout().byteOffset(groupElement(descriptor.name().toLowerCase()));
			case LOWER_XMM, LOWER_YMM, LOWER_ZMM -> saved_state.zmm$offset() + 64L * descriptor.indexWithinBank();
			case UPPER_XMM, UPPER_YMM, UPPER_ZMM -> saved_state.zmm$offset() + 64L * (descriptor.indexWithinBank() + 16);
			case MMX -> saved_state.mm$offset() + 8L * descriptor.indexWithinBank();

			default -> throw new IllegalStateException("Register not in saved state: " + descriptor);
		};
	}
}
