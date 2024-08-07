package ax.xz.fuzz.instruction;

import ax.xz.fuzz.instruction.x86.x86RegisterDescriptor;
import com.fasterxml.jackson.annotation.JsonSubTypes;

@JsonSubTypes({
	@JsonSubTypes.Type(value = x86RegisterDescriptor.class, name = "x86"),
})
public interface RegisterDescriptor {
	int index();
	int widthBytes();
	RegisterSet related();
}
