package ax.xz.fuzz.encoding;

public record AffectedFlag(String flag, FlagAccess access) {
	sealed interface FlagAccess {
		record Unknown() implements FlagAccess {}
		record Known(boolean read, boolean write) implements FlagAccess {}
	}
}
