package ax.xz.fuzz;

import java.util.random.RandomGenerator;

public interface PreservingMutation {
	boolean appliesTo(byte[] instruction);
	byte[] mutate(byte[] instruction);
}
