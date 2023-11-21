package ax.xz.fuzz.encoding;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PrefixFlag {
	LOCK, XACQUIRE, XRELEASE, NOTRACK, BOUND, REPEREPZ, REPNEREPNZ, REP, BRANCH_HINTS, LOCKLESS_HLE;
	@JsonCreator
	public static PrefixFlag fromString(String s) {
		var name = s.replace("accepts_", "").toUpperCase();
		for (var value : values()) {
			if (value.name().equals(name)) {
				return value;
			}
		}

		throw new IllegalArgumentException("Unknown prefix flag: " + s);
	}
}
