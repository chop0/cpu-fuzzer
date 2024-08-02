//package ax.xz.fuzz.runtime;
//
//import ax.xz.fuzz.runtime.state.GeneralPurposeRegisters;
//import ax.xz.fuzz.runtime.state.VectorRegisters;
//
//import java.util.zip.DataFormatException;
//
//import static ax.xz.fuzz.tester.slave_h.*;
//
//public class AssemblerTestCase {
//	private String zmm
//
//	public String create(RecordedTestCase rtc) {
//
//	}
//
//	private String gprs(GeneralPurposeRegisters r) {
//		var sb = new StringBuilder();
//		sb.append("gprs_construct:\n");
//		for (int i = 0; i < 16; i++) {
//			if (!GeneralPurposeRegisters.name(i).equals("rsp"))
//				sb.append("\tmov ").append(GeneralPurposeRegisters.name(i)).append(", ").append(Long.toHexString(r.values()[i])).append("h\n");
//		}
//		sb.append("\tret\n\n");
//		return sb.toString();
//	}
//
//	private String createRegion(RecordedTestCase.SerialisedRegion region, int idx) throws DataFormatException {
//		var sb = new StringBuilder();
//		sb.append("region_").append(idx).append("_construct:\n");
//		sb.append("\tmov rdi, ").append(region.start()).append("\n");
//		sb.append("\tmov rsi, ").append(region.size()).append("\n");
//		sb.append("\tmov rdx, ").append(PROT_READ() | PROT_WRITE() | PROT_EXEC()).append("\n");
//		sb.append("\tmov r10, ").append(MAP_PRIVATE() | MAP_ANONYMOUS() | MAP_FIXED()).append("\n");
//		sb.append("\tmov r8, -1\n");
//		sb.append("\tmov r9, 0\n");
//		sb.append("\tmov eax, 9\n");
//		sb.append("\tsyscall\n");
//
//		var data = region.decompressed();
//		boolean allZero = true;
//		for (byte b : data) {
//			if (b != 0) {
//				allZero = false;
//				break;
//			}
//		}
//
//		if (allZero) {
//			sb.append("ret\n\n");
//			return sb.toString();
//		} else
//			throw new UnsupportedOperationException("unimplemented");
//	}
//}
