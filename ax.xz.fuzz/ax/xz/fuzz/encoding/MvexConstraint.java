package ax.xz.fuzz.encoding;

public record MvexConstraint(String functionality, MaskMode maskMode, String staticBroadcast, boolean elementGranularity) {
	enum MaskMode {
		ALLOWED, FORBIDDEN, REQUIRED
	}
}
