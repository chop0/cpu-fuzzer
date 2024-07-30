package ax.xz.fuzz.instruction;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.blocks.randomisers.ReverseRandomGenerator;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.OpKind;

import java.util.ArrayList;
import java.util.random.RandomGenerator;

import static com.github.icedland.iced.x86.Register.NONE;

public sealed interface Operand {
	boolean fulfilledBy(ResourcePartition partition);

	boolean counted();

	void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException;

	void reverse(ReverseRandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException;

	record RmOperand(RegisterSet possibleRegisters, int bitSizeMem, int bitSizeBroadcast, boolean counted,
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

					if (operandIndex ==1 && instruction.getOpKind(0) == OpKind.REGISTER) {
						var firstOperand = instruction.getOpRegister(0);
						if (RegisterSet.LEGACY_HIGH_GP.hasRegister(firstOperand))
							possibleRegisters = possibleRegisters.subtract(RegisterSet.EXTENDED_GP);
						else if (RegisterSet.EXTENDED_GP.hasRegister(firstOperand))
							possibleRegisters = possibleRegisters.subtract(RegisterSet.LEGACY_HIGH_GP);
					}

					if (isMask) {
						assert possibleRegisters == RegisterSet.MASK;
						assert !counted;
						instruction.setOpMask(rp.selectRegister(RegisterSet.MASK, random));
					} else {
						assert counted;
						instruction.setOpKind(operandIndex, OpKind.REGISTER);
						instruction.setOpRegister(operandIndex, rp.selectRegister(possibleRegisters, random));
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
		public void reverse(ReverseRandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
			if (instruction.getOpKind(operandIndex) == OpKind.REGISTER) {
				random.pushInt(0);
				rp.reverseRegister(possibleRegisters, random, instruction.getOpRegister(operandIndex));
			} else if (instruction.getOpKind(operandIndex) == OpKind.MEMORY) {
				if (instruction.getBroadcast()) {
					random.pushInt(2);
					rp.reverseAddress(random, byteSizeBroadcast(), byteAlignBroadcast(), 4, instruction.getMemoryDisplacement64());
				} else {
					random.pushInt(1);
					rp.reverseAddress(random, byteSizeMem(), byteAlignMem(), 4, instruction.getMemoryDisplacement64());
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

		public static RmOperand register(int register) {
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
			return new RmOperand(RegisterSet.MASK, -1, -1, false, true);
		}
	}

	record Imm(int bitSize, int immediateOpType) implements Operand {
		@Override
		public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
			instruction.setOpKind(operandIndex, immediateOpType);

			long immediate = random.nextLong() >>> (64 - bitSize);
			instruction.setImmediate(operandIndex, immediate);
		}

		@Override
		public void reverse(ReverseRandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
			random.pushLong(instruction.getImmediate(operandIndex) << (64 - bitSize));
		}

		@Override
		public boolean fulfilledBy(ResourcePartition partition) {
			return true;
		}

		@Override
		public boolean counted() {
			return true;
		}
	}


	record VectorMultireg(int count, RegisterSet possibleStartRegisters) implements Operand {
		private RegisterSet intersection(ResourcePartition partition) {
			return partition.allowedRegisters().consecutiveBlocks(count, possibleStartRegisters);
		}

		@Override
		public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
			int register = intersection(rp).select(random);
			instruction.setOpKind(operandIndex, OpKind.REGISTER);
			instruction.setOpRegister(operandIndex, register);
		}

		@Override
		public void reverse(ReverseRandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
			intersection(rp).reverse(random, instruction.getOpRegister(operandIndex));
		}

		@Override
		public boolean fulfilledBy(ResourcePartition partition) {
			return !intersection(partition).isEmpty();
		}

		@Override
		public boolean counted() {
			return true;
		}

	}

	record VSIB(int indexWidth, RegisterSet possibleRegisters) implements Operand {
		@Override
		public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
		}

		@Override
		public void reverse(ReverseRandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {

		}

		@Override
		public boolean fulfilledBy(ResourcePartition partition) {
			return false; // TODO: implement
		}

		@Override
		public boolean counted() {
			return true;
		}

	}

	record TileStride() implements Operand {
		@Override
		public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
			// todo: do this
		}

		@Override
		public void reverse(ReverseRandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {

		}

		@Override
		public boolean fulfilledBy(ResourcePartition partition) {
			return false;
		}

		@Override
		public boolean counted() {
			return true;
		}

	}

	record Flags(StatusFlag flag) implements Operand {

		@Override
		public boolean fulfilledBy(ResourcePartition partition) {
			return partition.statusFlags().contains(flag);
		}

		@Override
		public boolean counted() {
			return false;
		}

		@Override
		public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {

		}

		@Override
		public void reverse(ReverseRandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {

		}
	}

	record AncillaryFlags(AncillaryFlag flag) implements Operand {
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

		@Override
		public void reverse(ReverseRandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
			switch (flag) {
				case ZEROING -> random.pushBoolean(instruction.getZeroingMasking());
				case SAE -> random.pushBoolean(instruction.getSuppressAllExceptions());
				case EMBEDDED_ROUNDING_CONTROL -> random.pushInt(instruction.getRoundingControl());
			}
		}

		enum AncillaryFlag {
			ZEROING,
			SAE,
			EMBEDDED_ROUNDING_CONTROL
		}

		public static AncillaryFlags zeroing() {
			return new AncillaryFlags(AncillaryFlag.ZEROING);
		}

		public static AncillaryFlags sae() {
			return new AncillaryFlags(AncillaryFlag.SAE);
		}

		public static AncillaryFlags erc() {
			return new AncillaryFlags(AncillaryFlag.EMBEDDED_ROUNDING_CONTROL);
		}
	}

	record FixedNumber(byte value) implements Operand {

		@Override
		public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
			instruction.setOpKind(operandIndex, OpKind.IMMEDIATE8);
			instruction.setImmediate8(value);
		}

		@Override
		public void reverse(ReverseRandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
			// do nothing
		}

		@Override
		public boolean fulfilledBy(ResourcePartition partition) {
			return true;
		}

		@Override
		public boolean counted() {
			return true;
		}

	}

	record Moffs(int bitSize) implements Operand {
		@Override
		public void select(RandomGenerator random, Instruction instruction, int operandIdx, ResourcePartition rp) throws NoPossibilitiesException {
			instruction.setOpKind(operandIdx, OpKind.MEMORY);
			instruction.setMemoryDisplSize(4);
			instruction.setMemoryDisplacement32(random.nextInt());
		}

		public void reverse(ReverseRandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
			random.pushInt(instruction.getMemoryDisplacement32());
		}

		@Override
		public boolean fulfilledBy(ResourcePartition partition) {
			return false; // todo: figure this out
		}

		@Override
		public boolean counted() {
			return true;
		}

	}

	record ImplicitReg(int register) implements Operand {
		@Override
		public void select(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {

		}

		@Override
		public void reverse(ReverseRandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {

		}

		@Override
		public boolean fulfilledBy(ResourcePartition partition) {
			return partition.allowedRegisters().hasRegister(register);
		}

		@Override
		public boolean counted() {
			return false;
		}

	}
}
