package ax.xz.fuzz.runtime.state;

import java.util.ArrayList;
import java.util.List;

public record StateDifference(CPUState a, CPUState b) {
	public List<RegisterDifference> registerDifferences() {
		var differences = new ArrayList<RegisterDifference>();

		differences.addAll(a.gprs().diff(b.gprs()));
		differences.addAll(a.zmm().diff(b.zmm()));
		differences.addAll(a.mmx().diff(b.mmx()));

		return differences;
	}
}
