package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.mutate.DeferredMutation;
import ax.xz.fuzz.mutate.MutationFactory;
import ax.xz.fuzz.runtime.CPUState;
import ax.xz.fuzz.runtime.ExecutionResult;
import ax.xz.fuzz.runtime.Tester;
import com.github.icedland.iced.x86.Code;
import com.github.icedland.iced.x86.FlowControl;
import com.github.icedland.iced.x86.Instruction;

import java.lang.foreign.MemorySegment;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.random.RandomGenerator;

import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.Code.*;

public class BlockGenerator {
	private static final Set<Integer> blacklistedOpcodes = Set.of(XGETBV, WRPKRU, RDSEED_R16, RDSEED_R32, RDSEED_R64, RDTSC, RDTSCP, RDPMC, RDRAND_R16, RDRAND_R32, RDRAND_R64, XRSTOR_MEM, XRSTORS_MEM, XRSTOR64_MEM, XRSTORS64_MEM, RDPID_R32, RDPID_R64, RDPRU, XSAVEOPT_MEM, XSAVEOPT64_MEM);
	private static final List<String> disallowedPrefixes = List.of("BND", "CCS", "MVEX", "KNC", "VIA", "XOP");

	private static final Opcode[] allOpcodes;

	static {
		allOpcodes = Arrays.stream(Code.class.getFields())
				.filter(field -> (field.getModifiers() & (Modifier.FINAL | Modifier.STATIC)) == (Modifier.FINAL | Modifier.STATIC))
				.map(field -> {
					int opCode;

					try {
						opCode = field.getInt(null);
					} catch (IllegalAccessException e) {
						throw new ExceptionInInitializerError(e);
					}

					if (!isOpcodeValid(field.getName(), opCode)) // skip control flow, privileged and mpx
						return null;

					return Opcode.of(opCode, field.getName());
				})
				.filter(Objects::nonNull)
				.filter(BlockGenerator::doesOpcodeWork)
				.toArray(Opcode[]::new);
	}

	private static boolean isOpcodeValid(String name, int opcode) {
		if (name.startsWith("F"))
			return false;

		var insn = Instruction.create(opcode);
		if (insn.isPrivileged() || insn.isStackInstruction() || insn.getFlowControl() != FlowControl.NEXT || insn.isJccShortOrNear())
			return false;

		if (disallowedPrefixes.stream().anyMatch(name::contains))
			return false;

		if (blacklistedOpcodes.contains(opcode)) {
			return false;
		}

		return true;
	}

	private static boolean doesOpcodeWork(Opcode opcode) {
		var scratch = mmap(MemorySegment.ofAddress(0x50000000), 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(),
				MAP_FIXED() | MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0).asSlice(0, 4096);
		if (scratch.address() == MAP_FAILED().address())
			throw new RuntimeException("mmap failed");

		try {
			var rp = ResourcePartition.all(false, scratch);

			var insn = opcode.configureRandomly(new Random(0), rp);

			Opcode[] opcodes = {opcode};
			Instruction[] instructions = {insn};

			var result = Tester.runBlock(CPUState.filledWith(0), opcodes, instructions);
			return !(result instanceof ExecutionResult.Fault.Sigill);
		} catch (BasicBlock.UnencodeableException e) {
			return false;
		} finally {
			munmap(scratch, scratch.byteSize());
		}
	}

	private final Set<Opcode> disabled = new HashSet<>();
	private ResourcePartition resourcePartition;

	public BlockGenerator(ResourcePartition resourcePartition) {
		this.resourcePartition = resourcePartition;
	}

	public void setPartition(ResourcePartition partition) {
		resourcePartition = partition;
	}

	public BasicBlock createBasicBlock(RandomGenerator rng) throws BlockGenerator.NoPossibilitiesException {
		var mf = new MutationFactory();

		var instructions = new Instruction[rng.nextInt(	1, 10)];
		var mutations = new DeferredMutation[instructions.length][];
		var opcodes = new Opcode[instructions.length];

		for (int i = 0; i < instructions.length; i++) {
			for (;;) {
				var variant = allOpcodes[rng.nextInt(allOpcodes.length)];
				if (disabled.contains(variant) || !variant.fulfilledBy(true, resourcePartition)) continue;

				instructions[i] = variant.ofRandom(resourcePartition, rng);
				mutations[i] = mf.createMutations(instructions[i], resourcePartition, rng);
				opcodes[i] = variant;
				break;
			}
		}

		return new BasicBlock(opcodes, instructions, mutations);
	}

	public static class NoPossibilitiesException extends Exception {

	}
}
