package ax.xz.fuzz.runtime;

import ax.xz.fuzz.arch.CPUState;


public sealed interface ExecutionResult {

	sealed interface Fault extends ExecutionResult {


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
