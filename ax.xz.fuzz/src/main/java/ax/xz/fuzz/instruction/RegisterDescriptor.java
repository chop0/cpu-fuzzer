package ax.xz.fuzz.instruction;

public interface RegisterDescriptor {
	int index();
	int widthBytes();
	RegisterSet related();
}
