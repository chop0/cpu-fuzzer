package ax.xz.fuzz.mutate;

public interface DeferredMutation {
	byte[] perform(byte[] instruction);
}
