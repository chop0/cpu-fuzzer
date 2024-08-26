package ax.xz.fuzz.riscv;

public record Label(String name) {
	@Override
	public boolean equals(Object obj) {
		return this == obj;
	}

	@Override
	public int hashCode() {
		return System.identityHashCode(this);
	}
}
