import ax.xz.fuzz.runtime.SegmentExecutor;
import ax.xz.fuzz.x86.runtime.X86SegmentExecutorProvider;

module ax.xz.fuzz.x86 {
	requires ax.xz.fuzz.x86.arch;
	requires ax.xz.fuzz;
	requires ax.xz.xed;
	requires com.github.icedland.iced.x86;

	provides SegmentExecutor.Provider with X86SegmentExecutorProvider;
}