package ax.xz.fuzz.runtime;

import ax.xz.fuzz.tester.execution_result;
import ax.xz.fuzz.tester.fault_details;

import java.lang.foreign.MemorySegment;


public sealed interface ExecutionResult {
	public static boolean interestingMismatch(ExecutionResult a, ExecutionResult b) {
		if (a instanceof Success A && b instanceof Success B) {
			return A.state().gprs().r15() == B.state().gprs().r15() &&
			       (!A.state().gprs().equals(B.state().gprs())
			       || !A.state().zmm().equals(B.state().zmm())
			       || !A.state().mmx().equals(B.state().mmx()));
		}

		return false;
	}

	static ExecutionResult ofStruct(MemorySegment struct) {
		if (execution_result.faulted(struct))
			return Fault.ofFaultDetails(execution_result.state(struct));
		else
			return Success.ofSavedState(execution_result.state(struct));
	}

	default void toStruct(MemorySegment struct) {
		switch (this) {
			case Fault fault -> {
				execution_result.faulted(struct, true);

				var faultDetails = execution_result.fault(struct);
				fault.toFaultDetails(faultDetails);
			}
			case Success success -> {
				execution_result.faulted(struct, false);

				var savedState = execution_result.state(struct);
				success.state().toSavedState(savedState);
			}
		}
	}

	sealed interface Fault extends ExecutionResult {
		static Fault ofFaultDetails(MemorySegment faultDetails) {

			return switch (fault_details.fault_reason(faultDetails)) {
				case 11 -> new Fault.Sigsegv(fault_details.fault_address(faultDetails).address());
				case 4 ->
						new Fault.Sigill(fault_details.fault_address(faultDetails).address(), SigillReason.fromOsValue(fault_details.fault_code(faultDetails)));
				case 7 -> new Fault.Sigbus(fault_details.fault_address(faultDetails).address());
				case 8 -> new Fault.Sigfpe(fault_details.fault_address(faultDetails).address());
				case 5 -> new Fault.Sigtrap(fault_details.fault_address(faultDetails).address());
				default -> new Fault.Unknown(fault_details.fault_address(faultDetails).address(), fault_details.fault_reason(faultDetails));
			};
		}

		default void toFaultDetails(MemorySegment faultDetails) {
			int reason = switch (this) {
				case Sigsegv _ -> 11;
				case Sigill _ -> 4;
				case Sigbus _ -> 7;
				case Sigfpe _ -> 8;
				case Sigtrap _ -> 5;
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
