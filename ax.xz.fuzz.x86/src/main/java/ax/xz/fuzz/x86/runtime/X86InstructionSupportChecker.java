package ax.xz.fuzz.x86.runtime;

import ax.xz.xed.CpuidGroup;
import ax.xz.xed.CpuidRecord;
import ax.xz.xed.XedInstruction;

public class X86InstructionSupportChecker {
	public boolean isSupported(XedInstruction instruction) {
		if (instruction.iform().isaSet().groups().isEmpty())
			return true;

		for (CpuidGroup group : instruction.iform().isaSet().groups()) {
			if (groupSupported(group)) {
				return true;
			}
		}

		return false;
	}

	private boolean groupSupported(CpuidGroup group) {
		for (CpuidRecord record : group.records()) {
			if (!recordSupported(record)) {
				return false;
			}
		}
		return true;
	}

	private boolean recordSupported(CpuidRecord record) {
		var cpuidResult = Cpuid.cpuid(record.leaf(), record.subleaf());
		var desiredValue = switch(record.register()) {
			case EAX -> cpuidResult.eax();
			case EBX -> cpuidResult.ebx();
			case ECX -> cpuidResult.ecx();
			case EDX -> cpuidResult.edx();
			default -> throw new IllegalArgumentException("Unknown register for cpuid: " + record.register());
		};

		desiredValue >>>= record.bitStart();
		desiredValue &= (1 << (record.bitEnd() - record.bitStart() + 1)) - 1;

		return desiredValue == record.value();
	}
}
