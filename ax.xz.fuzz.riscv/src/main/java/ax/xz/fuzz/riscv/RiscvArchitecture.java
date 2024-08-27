package ax.xz.fuzz.riscv;

import ax.xz.fuzz.arch.Architecture;
import ax.xz.fuzz.arch.BranchDescription;
import ax.xz.fuzz.arch.CPUState;
import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.mutate.Mutator;
import ax.xz.fuzz.riscv.base.RiscvBaseModule;
import ax.xz.fuzz.riscv.base.RiscvBaseRegister;
import ax.xz.fuzz.runtime.Config;
import ax.xz.fuzz.runtime.ExecutableSequence;
import ax.xz.fuzz.runtime.ExecutionResult;

import java.lang.foreign.MemorySegment;
import java.util.*;
import java.util.random.RandomGenerator;

import static ax.xz.fuzz.riscv.base.RiscvBaseRegisters.x0;
import static ax.xz.fuzz.riscv.base.RiscvBaseRegisters.x10;
import static ax.xz.fuzz.riscv.tester.slave_h.trampoline_return_address;

import static ax.xz.fuzz.runtime.ExecutionResult.*;


public class RiscvArchitecture implements Architecture {
	private final RiscvBaseModule baseModule;
	private final List<RiscvModule> modules;
	private final Opcode[] allOpcodes;

	private final List<RiscvRegister> registers;
	private final Map<RiscvRegister, Integer> registerIndices;
	private final Map<String, RiscvRegister> registerNames;
	private RegisterSet registersSet;
	private RegisterSet gprs;

	public RiscvArchitecture(RiscvBaseModule baseModule, RiscvModule... otherModules) {
		this.baseModule = baseModule;
		int currentRegisterIndex = 0;

		var registers = new ArrayList<RiscvRegister>();
		var registerIndices = new HashMap<RiscvRegister, Integer>();
		var registerNames = new HashMap<String, RiscvRegister>();

		var modulesList = new ArrayList<RiscvModule>();
		modulesList.add(baseModule);
		modulesList.addAll(List.of(otherModules));

		for (var module : modulesList) {
			for (var register : module.registers()) {
				registers.add(register);
				registerIndices.put(register, currentRegisterIndex);
				registerNames.put(register.toString(), register);
				currentRegisterIndex++;
			}
		}


		this.modules = Collections.unmodifiableList(modulesList);
		this.registers = Collections.unmodifiableList(registers);
		this.registerIndices = Collections.unmodifiableMap(registerIndices);
		this.registerNames = Collections.unmodifiableMap(registerNames);
		this.allOpcodes = modules.stream().flatMap(n -> n.opcodes().stream())
			.filter(n -> !n.isControlFlow())
			.toArray(Opcode[]::new);
	}

	@Override
	public RegisterDescriptor registerByIndex(int index) {
		return registers.get(index);
	}

	@Override
	public RegisterDescriptor registerByName(String name) {
		return registerNames.get(name);
	}

	@Override
	public RegisterSet trackedRegisters() {
		return registersSet();
	}

	public RegisterSet registersSet() {
		if (registersSet == null) {
			this.registersSet = RegisterSet.of(registers.toArray(new RegisterDescriptor[0]));
			this.gprs = registersSet.stream().filter(r -> r instanceof RiscvBaseRegister.Gpr).collect(RegisterSet.collector());
		}

		return registersSet;
	}

	@Override
	public RegisterSet validRegisters() {
		return registersSet();
	}

	@Override
	public RegisterSet[] subregisterSets() {
		return registersSet.stream().map(n -> RegisterSet.of(n)).toArray(RegisterSet[]::new);
	}

	@Override
	public Opcode[] allOpcodes() {
		return allOpcodes;
	}

	@Override
	public RegisterDescriptor defaultCounter() {
		return baseModule.gpr(5);
	}

	@Override
	public RegisterDescriptor stackPointer() {
		return baseModule.gpr(2);
	}

	@Override
	public ExecutionResult runSegment(MemorySegment code, CPUState initialState) {
		return new RiscvTestExecutor(this).runCode(gprs, code, initialState);
	}

	@Override
	public BranchDescription unconditionalJump() {
		return RiscvBranch.unconditional();
	}

	@Override
	public BranchDescription randomBranchType(ResourcePartition master, RandomGenerator rng) throws NoPossibilitiesException {
		var types = RiscvBranch.RiscvBranchType.values();

		int idx = rng.nextInt(types.length + 1);
		if (idx == types.length || !master.allowedRegisters().intersects(gprs))
			return RiscvBranch.unconditional();

		var type = types[idx];

		var rs1 = (RiscvBaseRegister.Gpr) master.selectRegister(gprs, rng);
		var rs2 = (RiscvBaseRegister.Gpr) master.selectRegister(gprs, rng);

		return new RiscvBranch(type, rs1, rs2);
	}

	@Override
	public Mutator[] allMutators() {
		return new Mutator[0];
	}

	@Override
	public int encode(ExecutableSequence sequence, MemorySegment code, Config config) {
		if (!(config.counterRegister() instanceof RiscvBaseRegister.Gpr counter))
			throw new IllegalArgumentException("Counter register must be a GPR");

		int counterBound = config.branchLimit();

		var blocks = sequence.blocks();
		var branches = sequence.branches();

		var blockAssembler = new RiscvAssembler(code.address(), this);

		// 1. our entrypoint
		blockAssembler.addi(counter, x0, counterBound);
		// exitpoint
		var exit = blockAssembler.label("exit");

		Label[] blockHeaders = new Label[blocks.length + 1];
		Label[] testCaseLocs = new Label[blocks.length];
		for (int i = 0; i < blockHeaders.length - 1; i++) {
			blockHeaders[i] = blockAssembler.label("block" + i);
			testCaseLocs[i] = blockAssembler.label("block" + i);
		}

		blockHeaders[blockHeaders.length - 1] = exit;

		for (int i = 0; i < blocks.length; i++) {
			blockAssembler.bind(blockHeaders[i]);

			blockAssembler.beq(counter, x0, exit);
			blockAssembler.addi(counter, counter, -1);

			for (var item : blocks[i].items()) {
				if (item == null)
					throw new IllegalArgumentException("instruction must not be null");

				blockAssembler.db(item.encode(blockAssembler.pc()));
			}

			switch (branches[i].type()) {
				case RiscvBranch t -> t.perform(blockAssembler, blockHeaders[branches[i].takenIndex()]);
				default ->
					throw new IllegalArgumentException("Unknown branch type (are you trying to replay a test case from the wrong architecture?): " + branches[i].type());
			}

			blockAssembler.j(blockHeaders[branches[i].notTakenIndex()]);
		}

		blockAssembler.bind(exit);
		blockAssembler.li(x10, trampoline_return_address().address());
		blockAssembler.j(x10);

		return blockAssembler.assemble(code);
	}

	@Override
	public String disassemble(byte[] code) {
		return new RiscvDisassembler(architecture()).disassemble(code);
	}

	@Override
	public boolean interestingMismatch(ExecutionResult a, ExecutionResult b) {
		if (a.equals(b))
			return false;

		record MismatchSet(ExecutionResult a, ExecutionResult b) {
		}

		return switch (new MismatchSet(a, b)) {
			case MismatchSet(Success _, _),
			     MismatchSet(_, Success _) -> true;

			case MismatchSet(Fault.Sigalrm _, _),
			     MismatchSet(_, Fault.Sigalrm _) -> true;

			case MismatchSet(Fault _, Fault _) -> false;

			default -> throw new MatchException("what the fuck " + a + " " + b, new Exception());
		};
	}

	@Override
	public int registerIndex(RegisterDescriptor descriptor) {
		return registerIndices.get(descriptor);
	}

	private RiscvArchitecture architecture() {
		var active = activeArchitecture.orElseThrow(() -> new UnsupportedOperationException("No active architecture"));

		return switch (active) {
			case RiscvArchitecture a -> a;
			default -> throw new UnsupportedOperationException("Active architecture is not RISC-V");
		};
	}

	public RegisterSet gprs() {
		if (gprs == null)
			registersSet();

		return gprs;
	}

	public RiscvBaseModule baseModule() {
		return baseModule;
	}

	@Override
	public String toString() {
		return "rv%d%s".formatted(baseModule.xlenBytes() * 8, modules.toString());
	}
}
