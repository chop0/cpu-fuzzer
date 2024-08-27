module ax.xz.fuzz.riscv {
	requires ax.xz.fuzz;

	provides ax.xz.fuzz.arch.ArchitectureProvider with ax.xz.fuzz.riscv.RiscvArchitectureProvider;
}