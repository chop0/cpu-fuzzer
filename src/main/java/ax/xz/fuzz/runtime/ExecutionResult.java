package ax.xz.fuzz.runtime;

import ax.xz.fuzz.runtime.state.CPUState;
import ax.xz.fuzz.tester.fault_details;

import java.lang.foreign.MemorySegment;


public sealed interface ExecutionResult {

	sealed interface Fault extends ExecutionResult {


		default void toFaultDetails(MemorySegment faultDetails) {
			int reason = switch (this) {
				case Sigsegv _ -> 11;
				case Sigill _ -> 4;
				case Sigbus _ -> 7;
				case Sigfpe _ -> 8;
				case Sigtrap _ -> 5;
				case Sigalrm _ -> 14;
				case Unknown unknown -> unknown.reason();
			};

			int faultCode = switch (this) {
				case Sigill sigill -> sigill.reason().osValue;
				default -> 0;
			};

			fault_details.fault_reason(faultDetails, reason);
			fault_details.fault_address(faultDetails, MemorySegment.ofAddress(address()));
			fault_details.fault_code(faultDetails, faultCode);
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

		record Sigalrm(long address) implements Fault {
			@Override
			public String toString() {
				return "Sigalrm[0x%08x]".formatted(address);
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

	}
}
