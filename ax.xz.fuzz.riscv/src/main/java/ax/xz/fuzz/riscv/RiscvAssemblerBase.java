package ax.xz.fuzz.riscv;

import ax.xz.fuzz.riscv.base.RiscvBaseOpcode;
import ax.xz.fuzz.riscv.base.RiscvBaseRegister;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ax.xz.fuzz.riscv.base.RiscvBaseField.*;
import static ax.xz.fuzz.riscv.base.RiscvBaseOpcode.ArithmeticImmOpcode.ADDI;
import static ax.xz.fuzz.riscv.base.RiscvBaseOpcode.ArithmeticOpcode.ADD;
import static ax.xz.fuzz.riscv.base.RiscvBaseOpcode.JumpOpcode.JAL;
import static ax.xz.fuzz.riscv.base.RiscvBaseOpcode.JumpOpcode.JALR;
import static ax.xz.fuzz.riscv.base.RiscvBaseOpcode.UimmOpcode.AUIPC;
import static ax.xz.fuzz.riscv.base.RiscvBaseOpcode.UimmOpcode.LUI;
import static ax.xz.fuzz.riscv.base.RiscvBaseRegisters.x0;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

public sealed class RiscvAssemblerBase permits RiscvAssembler{
	protected final RiscvArchitecture architecture;

	private final long startPc;

	private final Map<Label, Integer> blockLocations = new HashMap<>();
	private final List<RiscvInstructionBuilder> instructions = new ArrayList<>();
	private final List<Relocation> relocations = new ArrayList<>();

	public RiscvAssemblerBase(RiscvArchitecture architecture, long startPc) {
		this.architecture = architecture;
		this.startPc = startPc;
	}

	public Label label(String name) {
		return new Label(name);
	}

	public Label label() {
		return new Label(null);
	}

	public void arithmetic(RiscvBaseOpcode.ArithmeticOpcode opcode, RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2) {
		instructions.add(RiscvInstructionBuilder.of(architecture, opcode)
			.setField(RD, rd.index())
			.setField(RS1, rs1.index())
			.setField(RS2, rs2.index()));
	}

	public void arithmetic(RiscvBaseOpcode.ArithmeticImmOpcode opcode, RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		var insn = RiscvInstructionBuilder.of(architecture, opcode)
			.setField(RD, rd.index())
			.setField(RS1, rs1.index());

		switch (opcode) {
			case SLLI, SRLI, SRAI -> insn.setField(IMM_I_04, imm & 0b11111);
			default -> insn.setField(IMM_I_UNCONSTRAINED, imm);
		}

		instructions.add(insn);
	}

	public void bind(Label label) {
		blockLocations.put(label, instructions.size());
	}

	public void load(RiscvBaseOpcode.LoadOpcode opcode, RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		instructions.add(RiscvInstructionBuilder.of(architecture, opcode)
			.setField(RD, rd.index())
			.setField(RS1, rs1.index())
			.setField(IMM_I_UNCONSTRAINED, imm));
	}

	public void li(RiscvBaseRegister.Gpr rd, long imm) {
		if (imm == 0) {
			arithmetic(ADD, rd, x0, x0);
		} else {
			var upper = (int) (imm >>> 12);
			var lower = (int) imm;

			instructions.add(RiscvInstructionBuilder.of(architecture, LUI)
				.setField(RD, rd.index())
				.setField(IMM_U, upper));

			if (lower != 0) {
				instructions.add(RiscvInstructionBuilder.of(architecture, ADDI)
					.setField(RD, rd.index())
					.setField(RS1, rd.index())
					.setField(IMM_I_UNCONSTRAINED, lower));
			}
		}
	}

	public void loadAddress(RiscvBaseRegister.Gpr rd, Label location) {
		instructions.add(null);
		instructions.add(null);

		relocations.add(new Relocation.LoadAddress(instructions.size() - 2, location, rd));
	}

	public void branch(RiscvBaseOpcode.BranchOpcode opcode, RiscvBaseRegister.Gpr rs1, RiscvBaseRegister.Gpr rs2, Label location) {
		var builder = RiscvInstructionBuilder.of(architecture, opcode)
			.setField(RS1, rs1.index())
			.setField(RS2, rs2.index());

		if (blockLocations.containsKey(location))
			builder.setField(IMM_B, (blockLocations.get(location) - instructions.size()) * 4);
		else
			relocations.add(new Relocation.Branch(instructions.size(), location));

		instructions.add(builder);
	}

	public void jal(RiscvBaseRegister.Gpr rd, Label target) {
		var builder = RiscvInstructionBuilder.of(architecture, JAL)
			.setField(RD, rd.index());

		if (blockLocations.containsKey(target))
			builder.setField(IMM_J, (blockLocations.get(target) - instructions.size()) * 4);
		else
			relocations.add(new Relocation.Jump(instructions.size(), target));

		instructions.add(builder);
	}

	public void j(RiscvBaseRegister.Gpr rd) {
		instructions.add(RiscvInstructionBuilder.of(architecture, JAL)
			.setField(RD, rd.index())
			.setField(IMM_J, 0));
	}

	public void jalr(RiscvBaseRegister.Gpr rd, RiscvBaseRegister.Gpr rs1, int imm) {
		instructions.add(RiscvInstructionBuilder.of(architecture, JALR)
			.setField(RD, rd.index())
			.setField(RS1, rs1.index())
			.setField(IMM_I_UNCONSTRAINED, imm));
	}

	public void data(byte[] bytes) {
		instructions.add(RiscvInstructionBuilder.data(architecture, bytes));
	}

	public void load(RiscvBaseOpcode.LoadOpcode opcode, RiscvBaseRegister.Gpr rd, Label location) {
		loadAddress(rd, location);
		load(opcode, rd, rd, 0);
	}

	public long pc() {
		return startPc + instructions.size() * 4L;
	}

	public int assemble(MemorySegment target) {
		doRelocations();
		int location = 0;

		for (int i = 0; i < instructions.size(); i++) {
			var insn = instructions.get(i);
			if (insn == null) {
				continue;
			}

			var encoded = insn.encode(i * 4L);

			target.asSlice(location, encoded.length).copyFrom(MemorySegment.ofArray(encoded));
			location += encoded.length;

		}

		return instructions.size() * 4;
	}

	public byte[] assemble() {
		var array = new byte[instructions.size() * 4];
		var segment = MemorySegment.ofArray(array);
		assemble(segment);
		return array;
	}

	private void doRelocations() {
		for (Relocation relocation : relocations) {
			switch (relocation) {
				case Relocation.Branch(int location, Label dst) -> {
					var target = blockLocations.get(dst);
					if (target == null) {
						throw new IllegalStateException("Label " + dst + " is not bound");
					}

					var offset = (target - location) * 4;
					if (offset < -2048 || offset > 2047) {
						throw new IllegalStateException("Jump offset out of range: " + offset);
					}

					var builder = instructions.get(location);
					builder.setField(IMM_B, offset);
				}

				case Relocation.Jump(int location, Label dst) -> {
					var target = blockLocations.get(dst);
					if (target == null) {
						throw new IllegalStateException("Label " + dst + " is not bound");
					}

					var offset = (target - location) * 4;
					if (offset < -1048576 || offset > 1048575) {
						throw new IllegalStateException("Jump offset out of range: " + offset);
					}

					var builder = instructions.get(location);
					builder.setField(switch (builder.opcode()) {
						case JAL -> IMM_J;
//						case JALR -> IMM_I_UNCONSTRAINED;
						default -> throw new IllegalStateException("Invalid opcode for jump relocation");
					}, offset);
				}

				case Relocation.LoadAddress(int location, Label dst, RiscvBaseRegister.Gpr addressRd) -> {
					var target = blockLocations.get(dst);
					if (target == null) {
						throw new IllegalStateException("Label " + dst + " is not bound");
					}

					var offset = (target - location) * 4;

					var builder1 = RiscvInstructionBuilder.of(architecture, AUIPC)
						.setField(RD, addressRd.index())
						.setField(IMM_U, offset);
					var builder2 = RiscvInstructionBuilder.of(architecture, ADDI)
						.setField(RD, addressRd.index())
						.setField(RS1, addressRd.index())
						.setField(IMM_I_UNCONSTRAINED, offset);

					instructions.set(location, builder1);
					instructions.set(location + 1, builder2);
				}
			}
		}
	}

	public sealed interface Relocation {
		record LoadAddress(int location, Label dst, RiscvBaseRegister.Gpr addressRd) implements Relocation {
		}

		record Branch(int location, Label dst) implements Relocation {
		}

		record Jump(int location, Label dst) implements Relocation {
		}
	}

}
