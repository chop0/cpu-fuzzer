package ax.xz.fuzz.instruction.x86;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.Operand;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;

import java.util.random.RandomGenerator;

public record AncillaryFlags(AncillaryFlag flag) implements Operand {
	@Override
	public boolean fulfilledBy(ResourcePartition partition) {
		return true;
	}

	@Override
	public boolean counted() {
		return false;
	}

	@Override
	public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
		switch (flag) {
			case ZEROING -> instruction.setZeroingMasking(random.nextBoolean());
			case SAE -> instruction.setSuppressAllExceptions(random.nextBoolean());
			case EMBEDDED_ROUNDING_CONTROL -> instruction.setRoundingControl(random.nextInt(5));
		}
	}

	enum AncillaryFlag {
		ZEROING,
		SAE,
		EMBEDDED_ROUNDING_CONTROL
	}

	public static ax.xz.fuzz.instruction.x86.AncillaryFlags zeroing() {
		return new ax.xz.fuzz.instruction.x86.AncillaryFlags(AncillaryFlag.ZEROING);
	}

	public static ax.xz.fuzz.instruction.x86.AncillaryFlags sae() {
		return new ax.xz.fuzz.instruction.x86.AncillaryFlags(AncillaryFlag.SAE);
	}

	public static ax.xz.fuzz.instruction.x86.AncillaryFlags erc() {
		return new ax.xz.fuzz.instruction.x86.AncillaryFlags(AncillaryFlag.EMBEDDED_ROUNDING_CONTROL);
	}
}
