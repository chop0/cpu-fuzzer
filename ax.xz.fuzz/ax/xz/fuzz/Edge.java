//package ax.xz.fuzz;
//
//import com.github.icedland.iced.x86.Code;
//import com.github.icedland.iced.x86.CodeWriter;
//import com.github.icedland.iced.x86.FlowControl;
//import com.github.icedland.iced.x86.Instruction;
//
//public sealed interface Edge {
//	int encodeEpilogue(int rip, CodeWriter writer, int targetA, int targetB);
//
//	record Frontedge(Instruction instruction) implements Edge {
//		public Frontedge {
//			if (instruction.getFlowControl() != FlowControl.CONDITIONAL_BRANCH)
//				throw new IllegalArgumentException("Instruction must be a conditional branch");
//		}
//
//		@Override
//		public int encodeEpilogue(int rip, CodeWriter writer, int targetA, int targetB) {
//			var conditionalBranch = instruction.copy();
//			conditionalBranch.set
//
//		}
//	}
//
//}
