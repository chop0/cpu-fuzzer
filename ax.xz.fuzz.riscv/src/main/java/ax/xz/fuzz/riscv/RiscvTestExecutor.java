package ax.xz.fuzz.riscv;

import ax.xz.fuzz.arch.CPUState;
import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.riscv.base.RiscvBaseRegister;
import ax.xz.fuzz.riscv.tester.*;
import ax.xz.fuzz.runtime.ExecutionResult;
import ax.xz.fuzz.runtime.SigillReason;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.HashMap;

import static ax.xz.fuzz.riscv.tester.slave_h.*;

public class RiscvTestExecutor {
	private final RiscvArchitecture architecture;

	public RiscvTestExecutor(RiscvArchitecture architecture) {
		this.architecture = architecture;
	}

	public ExecutionResult runCode(RegisterSet trackedRegisters, MemorySegment code, CPUState initialState) {
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

	private void storeState(RegisterSet descriptors, CPUState initialState, MemorySegment state) {
		for (var entry : initialState.values().entrySet()) {
			if (!descriptors.hasRegister(entry.getKey())) // TODO: store and load mask registers
				continue;

			if (!(entry.getKey() instanceof RiscvRegister descriptor))
				throw new IllegalArgumentException("Invalid register descriptor: " + entry.getKey());

			var value = entry.getValue();

			var offset = stateStructOffset(descriptor);
			state.asSlice(offset, descriptor.widthBytes()).copyFrom(MemorySegment.ofArray(value));
		}
	}

	private HashMap<RegisterDescriptor, byte[]> loadState(RegisterSet descriptors, MemorySegment state) {
		var map = new HashMap<RegisterDescriptor, byte[]>();

		for (var d : descriptors) {
			if (!(d instanceof RiscvRegister descriptor))
				throw new IllegalArgumentException("Invalid register descriptor: " + d);

			var offset = stateStructOffset(descriptor);
			var value = new byte[descriptor.widthBytes()];
			MemorySegment.ofArray(value).copyFrom(state.asSlice(offset, descriptor.widthBytes()));
			map.put(descriptor, value);
		}
		return map;
	}

	private long stateStructOffset(RiscvRegister descriptor) {
		return switch (descriptor) {
			case RiscvBaseRegister.Gpr(int index) -> index * 8L;
			default -> throw new IllegalArgumentException("Register not saved in state: " + descriptor);
		};
	}
}
