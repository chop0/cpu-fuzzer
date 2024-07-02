package ax.xz.fuzz.mutate;

import ax.xz.fuzz.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;

import java.util.ArrayList;
import java.util.random.RandomGenerator;

public class MutationFactory {
	private static final Mutator[] mutators = {
			new PrefixDuplicator()
	};

	public DeferredMutation[] createMutations(Instruction original, ResourcePartition partition, RandomGenerator rng) {
		var mutations = new ArrayList<DeferredMutation>();

		for (Mutator mutator : mutators) {
			if (mutator.appliesTo(original, partition)) {
				mutations.add(mutator.createMutation(original, rng, partition));
			}
		}

		return mutations.toArray(new DeferredMutation[0]);
	}
}
