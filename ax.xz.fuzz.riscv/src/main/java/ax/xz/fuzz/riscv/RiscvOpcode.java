package ax.xz.fuzz.riscv;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.InstructionBuilder;
import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;

import java.util.Map;
import java.util.random.RandomGenerator;

import static ax.xz.fuzz.arch.Architecture.activeArchitecture;

public interface RiscvOpcode extends Opcode {
	RiscvInstructionFormat format();
	Map<RiscvInstructionField, Integer> fieldConstraints();

	@Override
	default InstructionBuilder select(RandomGenerator rng, ResourcePartition resourcePartition) throws NoPossibilitiesException {
		var builder = RiscvInstructionBuilder.of((RiscvArchitecture) activeArchitecture(), this);

		var unsetFields = builder.unsetFields();

		for (RiscvInstructionField unsetField : unsetFields) {
			unsetField.select(builder, rng, resourcePartition);
		}

		return builder;
	}

	String disassemble(int instruction);

	@Override
	default boolean fulfilledBy(ResourcePartition rp) {
		for (var field : format().fields()) {
			if (!field.fulfilledBy(rp)) {
				return false;
			}
		}

		return true;
	}

	default boolean isInstance(int instruction) {
		for (var constraint : fieldConstraints().entrySet()) {
			if (constraint.getKey().get(instruction) != constraint.getValue())
				return false;
		}

		return true;
	}

	boolean isControlFlow();
}
