package ax.xz.fuzz;

import ax.xz.fuzz.tester.slave_h;

public enum SigillReason {
	ILL_ILLOPC(slave_h.ILL_ILLOPC()),
	ILL_ILLOPN(slave_h.ILL_ILLOPN()),
	ILL_ILLADR(slave_h.ILL_ILLADR()),
	ILL_ILLTRP(slave_h.ILL_ILLTRP()),
	ILL_PRVOPC(slave_h.ILL_PRVOPC()),
	ILL_PRVREG(slave_h.ILL_PRVREG()),
	ILL_COPROC(slave_h.ILL_COPROC()),
	ILL_BADSTK(slave_h.ILL_BADSTK());

	public final int osValue;
	SigillReason(int osValue) {
		this.osValue = osValue;
	}

	public static SigillReason fromOsValue(int osValue) {
		for (var v : values()) {
			if (v.osValue == osValue)
				return v;
		}
		throw new IllegalArgumentException("Unknown osValue: " + osValue);
	}
}
