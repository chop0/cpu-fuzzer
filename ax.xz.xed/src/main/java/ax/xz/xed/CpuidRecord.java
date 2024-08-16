package ax.xz.xed;

import java.lang.foreign.MemorySegment;

public record CpuidRecord(int leaf, int subleaf, XedRegister register, int bitStart, int bitEnd, int value) {

	public static CpuidRecord from(MemorySegment rec) {
		int leaf = xed_cpuid_rec_t.leaf(rec);
		int subleaf = xed_cpuid_rec_t.subleaf(rec);
		int reg = xed_cpuid_rec_t.reg(rec);
		int bitStart = xed_cpuid_rec_t.bit_start(rec);
		int bitEnd = xed_cpuid_rec_t.bit_end(rec);
		int value = xed_cpuid_rec_t.value(rec);

		return new CpuidRecord(leaf, subleaf, XedRegister.from(reg).orElseThrow(), bitStart, bitEnd, value);
	}
}
