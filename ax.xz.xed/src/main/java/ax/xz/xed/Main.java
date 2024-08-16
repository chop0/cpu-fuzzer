package ax.xz.xed;

import static ax.xz.xed.xed_internal_1.xed_inst_table;
import static ax.xz.xed.xed_internal_15.XED_MAX_INST_TABLE_NODES;

public class Main {
	static {
		X86LibraryLoader.load();
	}

	public static void main(String[] args) {
		for (int i = 1; i < XED_MAX_INST_TABLE_NODES(); i++) {
			var insn = xed_inst_table().asSlice(xed_inst_t.sizeof() * i, xed_inst_t.layout());
			System.out.println(XedInstruction.from(insn));

//			System.out.printf("%-30s %b%n", xed_iform_enum_t2str(i).getString(0), isSupported(i));
//			var info = xed_iform_map(i).get(JAVA_LONG_UNALIGNED, 0);
//			System.out.println(xed_iclass_enum_t2str((int) (info & 0xffff)).getString(0));
//			System.out.println(((info >>> (64 - 16)) & 0xffff));
		}
	}

}