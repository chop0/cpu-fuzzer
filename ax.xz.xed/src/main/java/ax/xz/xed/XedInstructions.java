package ax.xz.xed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ax.xz.xed.xed_internal_1.xed_inst_table;
import static ax.xz.xed.xed_internal_15.XED_MAX_INST_TABLE_NODES;

public class XedInstructions {
	static {
		X86LibraryLoader.load();
	}

	private static final List<XedInstruction> instructions;

	static {
		var result = new ArrayList<XedInstruction>();
		for (int i = 1; i < XED_MAX_INST_TABLE_NODES(); i++) {
			var insn = xed_inst_table().asSlice(xed_inst_t.sizeof() * i, xed_inst_t.layout());
			result.add(XedInstruction.from(insn));
		}

		instructions = Collections.unmodifiableList(result);
	}

	public static List<XedInstruction> instructions() {
		return instructions;
	}
}
