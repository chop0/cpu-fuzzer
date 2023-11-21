package ax.xz.fuzz;

import com.github.icedland.iced.x86.Code;
import com.github.icedland.iced.x86.EncodingKind;
import com.github.icedland.iced.x86.FlowControl;
import com.github.icedland.iced.x86.Instruction;

import java.lang.foreign.MemorySegment;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.random.RandomGenerator;

import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.FlowControl.*;

public class BlockGenerator {
	private static final Set<Integer> blacklistedOpcodes = Set.of(Code.WRPKRU, Code.RDSEED_R16, Code.RDSEED_R32, Code.RDSEED_R64, Code.RDTSC, Code.RDTSCP, Code.RDPMC, Code.RDRAND_R16, Code.RDRAND_R32, Code.RDRAND_R64, Code.XRSTOR_MEM, Code.XRSTORS_MEM, Code.XRSTOR64_MEM, Code.XRSTORS64_MEM, Code.RDPID_R32, Code.RDPID_R64, Code.RDPRU, Code.XSAVEOPT_MEM, Code.XSAVEOPT64_MEM);
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
		if (insn.isPrivileged() || insn.isStackInstruction() || insn.getFlowControl() != FlowControl.NEXT)
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
		var instructions = new Instruction[rng.nextInt(	1, 3)];
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

	public static class NoPossibilitiesException extends Exception {

	}
}
