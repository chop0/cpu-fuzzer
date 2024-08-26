package ax.xz.fuzz.instruction;

public interface RegisterDescriptor {
	int widthBytes();
	RegisterSet related();
}
