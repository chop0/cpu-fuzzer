package ax.xz.fuzz.runtime;

import ax.xz.fuzz.tester.fault_details;

import java.lang.foreign.MemorySegment;

import static ax.xz.fuzz.tester.execution_result.*;
import static ax.xz.fuzz.tester.fault_details.fault_address;
import static ax.xz.fuzz.tester.fault_details.fault_reason;

public sealed interface ExecutionResult {
	static ExecutionResult ofStruct(MemorySegment struct) {
		if (faulted(struct))
			return Fault.ofFaultDetails(state(struct));
		else
			return Success.ofSavedState(state(struct));
	}

	sealed interface Fault extends ExecutionResult {
		static Fault ofFaultDetails(MemorySegment faultDetails) {

			return switch (fault_reason(faultDetails)) {
				case 11 -> new Fault.Sigsegv(fault_address(faultDetails).address());
				case 4 ->
						new Fault.Sigill(fault_address(faultDetails).address(), SigillReason.fromOsValue(fault_details.fault_code(faultDetails)));
				case 7 -> new Fault.Sigbus(fault_address(faultDetails).address());
				case 8 -> new Fault.Sigfpe(fault_address(faultDetails).address());
				case 5 -> new Fault.Sigtrap(fault_address(faultDetails).address());
				default -> new Fault.Unknown(fault_address(faultDetails).address(), fault_reason(faultDetails));
			};
		}

		long address();

		record Sigbus(long address) implements Fault {
			@Override
			public String toString() {
				return "Sigbus[0x%08x]".formatted(address);
			}
		}

		record Sigill(long address, SigillReason reason) implements Fault {
			@Override
			public String toString() {
				return "Sigill[0x%08x, reason = %s]".formatted(address, reason);
			}
		}

		record Sigsegv(long address) implements Fault {
			public String toString() {
				return "Sigsegv[0x%08x]".formatted(address);
			}
		}

		record Sigfpe(long address) implements Fault {
			@Override
			public String toString() {
				return "Sigfpe[0x%08x]".formatted(address);
			}
		}

		record Sigtrap(long address) implements Fault {
			@Override
			public String toString() {
				return  "Sigtrap[0x%08x]".formatted(address);
			}
		}

		record Unknown(long address, int reason) implements Fault {
			@Override
			public String toString() {
				return  "Unknown[0x%08x, reason = %s]".formatted(address, reason);
			}
		}
	}

	record Success(CPUState state) implements ExecutionResult {

		public static Success ofSavedState(MemorySegment savedState) {
			return new Success(CPUState.ofSavedState(savedState));
		}
	}
}
