package ax.xz.fuzz;

import com.github.icedland.iced.x86.Code;
import com.github.icedland.iced.x86.Instruction;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.random.RandomGenerator;

import static com.github.icedland.iced.x86.FlowControl.NEXT;

public class InstructionGenerator {
	private static final Set<Integer> blacklistedOpcodes = Set.of(Code.WRPKRU, Code.RDSEED_R16, Code.RDSEED_R32, Code.RDSEED_R64, Code.RDTSC, Code.RDTSCP, Code.RDPMC, Code.RDRAND_R16, Code.RDRAND_R32, Code.RDRAND_R64, Code.XRSTOR_MEM, Code.XRSTORS_MEM, Code.XRSTOR64_MEM, Code.XRSTORS64_MEM, Code.RDPID_R32, Code.RDPID_R64, Code.RDPRU, Code.XSAVEOPT_MEM, Code.XSAVEOPT64_MEM);
	private static final List<String> disallowedPrefixes = List.of("VEX", "EVEX", "BND", "CCS", "MVEX", "KNC", "VIA", "XOP");

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

					var insn = Instruction.create(opCode);
					if (insn.getFlowControl() != NEXT
						|| field.getName().startsWith("F")
						|| insn.isPrivileged()
						|| insn.isStackInstruction()
						|| blacklistedOpcodes.contains(opCode) ||
						disallowedPrefixes.stream().anyMatch(n -> field.getName().contains(n))) // skip control flow, privileged and mpx
						return null;

					return Opcode.of(opCode, field.getName());
				})
				.filter(Objects::nonNull)
				.toArray(Opcode[]::new);
	}

	private final Set<Opcode> disabled = new HashSet<>();
	private ResourcePartition resourcePartition;

	public InstructionGenerator(boolean evex, ResourcePartition resourcePartition) {
		this.resourcePartition = resourcePartition;
	}

	public void setPartition(ResourcePartition partition) {
		resourcePartition = partition;
	}

	public BasicBlock createBasicBlock(RandomGenerator rng) throws InstructionGenerator.NoPossibilitiesException {
		var instructions = new Instruction[rng.nextInt(1, 100)];
		var opcodes = new Opcode[instructions.length];

		for (int i = 0; i < instructions.length; i++) {
			for (;;) {
				var variant = allOpcodes[rng.nextInt(allOpcodes.length)];
				if (disabled.contains(variant) || !variant.fulfilledBy(false, resourcePartition)) continue;

				instructions[i] = variant.ofRandom(resourcePartition, rng);
				opcodes[i] = variant;
				break;
			}
		}

		return new BasicBlock(opcodes, instructions);
	}

	public CombinedBlock createCombinedBlock(RandomGenerator rng) throws InstructionGenerator.NoPossibilitiesException {
		var lhs = createBasicBlock(rng);
		var rhs = createBasicBlock(rng);

		return CombinedBlock.randomlyInterleaved(rng, lhs, rhs);
	}

	public void handleUnencodeable(Opcode opcode) {
		disabled.add(opcode);
	}

	public static class NoPossibilitiesException extends Exception {

	}

}
