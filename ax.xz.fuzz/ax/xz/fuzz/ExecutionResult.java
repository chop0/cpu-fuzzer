package ax.xz.fuzz;

import ax.xz.fuzz.tester.fault_details;
import ax.xz.fuzz.tester.saved_state;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.util.stream.StreamSupport;

import static ax.xz.fuzz.tester.execution_result.*;
import static ax.xz.fuzz.tester.fault_details.fault_address$get;
import static ax.xz.fuzz.tester.fault_details.fault_reason$get;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public sealed interface ExecutionResult {
	static ExecutionResult ofStruct(MemorySegment struct) {
		if (faulted$get(struct))
			return Fault.ofFaultDetails(fault$slice(struct));
		else
			return Success.ofSavedState(state$slice(struct));
	}

	sealed interface Fault extends ExecutionResult {
		static Fault ofFaultDetails(MemorySegment faultDetails) {

			return switch (fault_reason$get(faultDetails)) {
				case 11 -> new Fault.Sigsegv(fault_address$get(faultDetails).address());
				case 4 ->
						new Fault.Sigill(fault_address$get(faultDetails).address(), SigillReason.fromOsValue(fault_details.fault_code$get(faultDetails)));
				case 7 -> new Fault.Sigbus(fault_address$get(faultDetails).address());
				case 8 -> new Fault.Sigfpe(fault_address$get(faultDetails).address());
				case 5 -> new Fault.Sigtrap(fault_address$get(faultDetails).address());
				default -> new Fault.Unknown(fault_address$get(faultDetails).address(), fault_reason$get(faultDetails));
			};
		}

		long address();

		record Sigbus(long address) implements Fault {
			@Override
			public String toString() {
				return STR. "Sigbus[0x\{ Long.toUnsignedString(address, 16) }]" ;
			}
		}

		record Sigill(long address, SigillReason reason) implements Fault {
			@Override
			public String toString() {
				return STR. "Sigill[0x\{ Long.toUnsignedString(address, 16) }, reason = \{ reason }]" ;
			}
		}

		record Sigsegv(long address) implements Fault {
			public String toString() {
				return STR. "Sigsegv[0x\{ Long.toUnsignedString(address, 16) }]" ;
			}
		}

		record Sigfpe(long address) implements Fault {
			@Override
			public String toString() {
				return STR. "Sigfpe[\{ Long.toUnsignedString(address, 16) }]" ;
			}
		}

		record Sigtrap(long address) implements Fault {
			@Override
			public String toString() {
				return STR. "Sigtrap[0x\{ Long.toUnsignedString(address, 16) }]" ;
			}
		}

		record Unknown(long address, int reason) implements Fault {
			@Override
			public String toString() {
				return STR. "Unknown[0x\{ Long.toUnsignedString(address, 16) }, reason = \{ reason }]" ;
			}
		}
	}

	record Success(CPUState state) implements ExecutionResult {

		public static Success ofSavedState(MemorySegment savedState) {
			return new Success(CPUState.ofSavedState(savedState));
		}
	}
}
