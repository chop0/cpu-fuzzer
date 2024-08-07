package ax.xz.fuzz.instruction.x86;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.Operand;
import ax.xz.fuzz.instruction.RegisterDescriptor;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.ResourcePartition;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.OpKind;

import java.util.ArrayList;
import java.util.random.RandomGenerator;

import static com.github.icedland.iced.x86.Register.NONE;

public record RmOperand(RegisterSet possibleRegisters, int bitSizeMem, int bitSizeBroadcast, boolean counted,
			boolean isMask) implements Operand {

	@Override
	public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
		enum Mode {
			REG, MEM, MEM_BROADCAST
		}

		var modes = new ArrayList<Mode>();
		if (possibleRegisters.intersects(rp.allowedRegisters()))
			modes.add(Mode.REG);

		if (rp.canFulfil(byteSizeMem(), byteAlignMem(), 4))
			modes.add(Mode.MEM);
		if (rp.canFulfil(byteSizeBroadcast(), byteAlignBroadcast(), 4))
			modes.add(Mode.MEM_BROADCAST);

		if (modes.isEmpty())
			throw new NoPossibilitiesException();

		switch (modes.get(random.nextInt(modes.size()))) {
			case Mode.REG -> {
				RegisterSet possibleRegisters = this.possibleRegisters;

				if (operandIndex == 1 && instruction.getOpKind(0) == OpKind.REGISTER) {
					var firstOperand = instruction.getOpRegister(0);
					if (x86RegisterBanks.LEGACY_HIGH_GP.hasRegister(x86RegisterDescriptor.byIcedId(firstOperand)))
						possibleRegisters = possibleRegisters.subtract(x86RegisterBanks.EXTENDED_GP);
					else if (x86RegisterBanks.EXTENDED_GP.hasRegister(x86RegisterDescriptor.byIcedId(firstOperand)))
						possibleRegisters = possibleRegisters.subtract(x86RegisterBanks.LEGACY_HIGH_GP);
				}

				if (isMask) {
					assert possibleRegisters == x86RegisterBanks.MASK;
					assert !counted;

					var selected = (x86RegisterDescriptor)rp.selectRegister(x86RegisterBanks.MASK, random);
					instruction.setOpMask(selected.icedId());
				} else {
					assert counted;
					instruction.setOpKind(operandIndex, OpKind.REGISTER);
					var selected = (x86RegisterDescriptor)rp.selectRegister(possibleRegisters, random);
					instruction.setOpRegister(operandIndex, selected.icedId());
				}
			}

			case Mode.MEM -> {
				instruction.setOpKind(operandIndex, OpKind.MEMORY);
				// todo:  support protected mode
				instruction.setMemoryBase(NONE);
				instruction.setMemoryIndex(NONE);
				instruction.setMemoryDisplSize(4);
				instruction.setMemoryDisplacement64(rp.selectAddress(random, byteSizeMem(), byteAlignMem(), 4));
			}

			case Mode.MEM_BROADCAST -> {
				instruction.setOpKind(operandIndex, OpKind.MEMORY);
				instruction.setBroadcast(true);
				// todo:  support protected mode
				instruction.setMemoryBase(NONE);
				instruction.setMemoryIndex(NONE);
				instruction.setMemoryDisplSize(4);
				instruction.setMemoryDisplacement64(rp.selectAddress(random, byteSizeBroadcast(), byteAlignBroadcast(), 4));
			}
		}
	}

	@Override
	public boolean fulfilledBy(ResourcePartition partition) {
		return partition.allowedRegisters().intersects(possibleRegisters())
		       || partition.canFulfil(byteSizeMem(), byteAlignMem(), 4)
		       || partition.canFulfil(byteSizeBroadcast(), byteAlignBroadcast(), 4);
	}

	int byteSizeMem() {
		if (bitSizeMem == -1)
			return -1;

		return bitSizeMem / 8;
	}

	int byteSizeBroadcast() {
		if (bitSizeBroadcast == -1)
			return -1;
		return bitSizeBroadcast / 8;
	}

	int byteAlignMem() {
		return byteSizeMem() == 0 ? 1 : byteSizeMem();
	}

	int byteAlignBroadcast() {
		return byteSizeBroadcast() == 0 ? 1 : byteSizeBroadcast();
	}

	public static RmOperand rm(RegisterSet possibleRegisters, int bitSizeMem, int bitSizeBroadcast) {
		return new RmOperand(possibleRegisters, bitSizeMem, bitSizeBroadcast, true, false);
	}

	public static RmOperand register(RegisterDescriptor register) {
		return new RmOperand(RegisterSet.of(register), -1, -1, true, false);
	}

	public static RmOperand register(RegisterSet possibleRegisters) {
		return new RmOperand(possibleRegisters, -1, -1, true, false);
	}

	public static RmOperand memory(int bitSizeMem) {
		return new RmOperand(RegisterSet.of(), bitSizeMem, -1, true, false);
	}

	public static RmOperand rm(RegisterSet possibleRegisters, int bitSizeMem) {
		return new RmOperand(possibleRegisters, bitSizeMem, -1, true, false);
	}

	public static RmOperand mask() {
		return new RmOperand(x86RegisterBanks.MASK, -1, -1, false, true);
	}
}
