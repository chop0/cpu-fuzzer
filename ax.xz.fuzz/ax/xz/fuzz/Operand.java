package ax.xz.fuzz;

import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.OpKind;

import java.util.ArrayList;
import java.util.random.RandomGenerator;

import static com.github.icedland.iced.x86.Register.NONE;

public sealed interface Operand {
	boolean fulfilledBy(ResourcePartition partition);

	sealed interface ExplicitOperand extends Operand {
	}

	sealed interface Counted extends ExplicitOperand {
		void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException;

		record RegOrMemBroadcastable(RegisterSet possibleRegisters, int bitSizeMem,
									 int bitSizeBroadcast) implements Counted {
			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException {
				enum Mode {
					REG, MEM, MEM_BROADCAST
				}

				var modes = new ArrayList<Mode>();
				if (!possibleRegisters.intersection(rp.allowedRegisters()).isEmpty())
					modes.add(Mode.REG);

				if (rp.canFulfil(byteSizeMem(), byteAlignMem(), 4))
					modes.add(Mode.MEM);
				if (rp.canFulfil(byteSizeBroadcast(), byteAlignBroadcast(), 4))
					modes.add(Mode.MEM_BROADCAST);

				if (modes.isEmpty())
					throw new BlockGenerator.NoPossibilitiesException();

				switch (modes.get(random.nextInt(modes.size()))) {
					case Mode.REG -> {
						instruction.setOpKind(operandIndex, OpKind.REGISTER);
						instruction.setOpRegister(operandIndex, rp.selectRegister(possibleRegisters, random));
					}

					case Mode.MEM -> {
						instruction.setOpKind(operandIndex, OpKind.MEMORY);
						// todo:  support protected mode
						instruction.setMemoryBase(NONE);
						instruction.setMemoryIndex(NONE);
						instruction.setMemoryDisplSize(4);
						instruction.setMemoryDisplacement64(rp.randomMemoryAddress(random, byteSizeMem(), byteAlignMem(), 4));
					}

					case Mode.MEM_BROADCAST -> {
						instruction.setOpKind(operandIndex, OpKind.MEMORY);
						instruction.setBroadcast(true);
						// todo:  support protected mode
						instruction.setMemoryBase(NONE);
						instruction.setMemoryIndex(NONE);
						instruction.setMemoryDisplSize(4);
						instruction.setMemoryDisplacement64(rp.randomMemoryAddress(random, byteSizeBroadcast(), byteAlignBroadcast(), 4));
					}
				}
			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return !partition.allowedRegisters().intersection(possibleRegisters()).isEmpty()
					   || partition.canFulfil(byteSizeMem(), byteAlignMem(), 4)
					   || partition.canFulfil(byteSizeBroadcast(), byteAlignBroadcast(), 4);
			}

			int byteSizeMem() {
				return bitSizeMem / 8;
			}

			int byteSizeBroadcast() {
				return bitSizeBroadcast / 8;
			}

			int byteAlignMem() {
				return byteSizeMem() == 0 ? 1 : byteSizeMem();
			}

			int byteAlignBroadcast() {
				return byteSizeBroadcast() == 0 ? 1 : byteSizeBroadcast();
			}
		}

		record RegOrMem(RegisterSet possibleRegisters, int bitSizeMem) implements Counted {
			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException {
				enum Mode {
					REG, MEM
				}

				var modes = new ArrayList<Mode>();
				if (!possibleRegisters.intersection(rp.allowedRegisters()).isEmpty())
					modes.add(Mode.REG);
				if (rp.canFulfil(byteSizeMem(), byteAlignMem(), 4))
					modes.add(Mode.MEM);

				switch (modes.get(random.nextInt(modes.size()))) {
					case Mode.REG -> {
						instruction.setOpKind(operandIndex, OpKind.REGISTER);
						instruction.setOpRegister(operandIndex, rp.selectRegister(possibleRegisters, random));
					}

					case Mode.MEM -> {
						instruction.setOpKind(operandIndex, OpKind.MEMORY);
						instruction.setMemoryBase(NONE);
						instruction.setMemoryIndex(NONE);
						instruction.setMemoryDisplSize(4);
						instruction.setMemoryDisplacement64(rp.randomMemoryAddress(random, byteSizeMem(), byteSizeMem() == 0 ? 1 : byteSizeMem(), instruction.getMemoryDisplSize()));
					}
				}
			}

			int byteSizeMem() {
				return bitSizeMem / 8;
			}

			public int byteAlignMem() {
				return byteSizeMem() == 0 ? 1 : byteSizeMem();
			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return !partition.allowedRegisters().intersection(possibleRegisters()).isEmpty()
					   || partition.canFulfil(byteSizeMem(), byteAlignMem(), 4);
			}
		}

		record Reg(RegisterSet possibleRegisters) implements Counted {
			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException {
				instruction.setOpKind(operandIndex, OpKind.REGISTER);
				instruction.setOpRegister(operandIndex, rp.selectRegister(possibleRegisters, random));
			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return !partition.allowedRegisters().intersection(possibleRegisters()).isEmpty();
			}
		}

		record Mem(int bitSize) implements Counted {
			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException {
				instruction.setOpKind(operandIndex, OpKind.MEMORY);
				instruction.setMemoryBase(NONE);
				instruction.setMemoryIndex(NONE);
				instruction.setMemoryDisplSize(4);
				instruction.setMemoryDisplacement64(rp.randomMemoryAddress(random, byteSize(), 16, instruction.getMemoryDisplSize()));
			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return partition.canFulfil(byteSize(), byteSize() == 0 ? 1 : byteSize(), 4);
			}

			int byteSize() {
				return bitSize / 8;
			}

			public int byteAlign() {
				return byteSize() == 0 ? 1 : byteSize();
			}
		}

		record Imm(int bitSize, int immediateOpType) implements Counted {
			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException {
				instruction.setOpKind(operandIndex, immediateOpType);

				long immediate = random.nextLong() >>> (64 - bitSize);
				instruction.setImmediate(operandIndex, immediate);
			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return true;
			}
		}

		// explicit because apparently the encoder includes this when finding the operand index
		record FixedReg(int register) implements Counted {
			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException {
				instruction.setOpKind(operandIndex, OpKind.REGISTER);
				instruction.setOpRegister(operandIndex, register);
			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return partition.allowedRegisters().hasRegister(register);
			}
		}

		record VectorMultireg(int count, RegisterSet possibleStartRegisters) implements Counted {
			private RegisterSet intersection(ResourcePartition partition) {
				return partition.allowedRegisters().consecutiveBlocks(count, possibleStartRegisters);
			}

			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException {
				int register = intersection(rp).choose(random);
				instruction.setOpKind(operandIndex, OpKind.REGISTER);
				instruction.setOpRegister(operandIndex, register);
			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return !intersection(partition).isEmpty();
			}
		}


		record VSIB(int indexWidth, RegisterSet possibleRegisters) implements Counted {
			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException {

			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return false; // TODO: implement
			}
		}

		record TileStride() implements Counted {
			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException {
				// todo: do this
			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return false;
			}
		}

		record FixedNumber(byte value) implements Counted {

			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException {
				instruction.setOpKind(operandIndex, OpKind.IMMEDIATE8);
				instruction.setImmediate8(value);
			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return true;
			}
		}

		record Moffs(int bitSize) implements Counted {
			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIdx, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException {
				instruction.setOpKind(operandIdx, OpKind.MEMORY);
				instruction.setMemoryDisplSize(4);
				instruction.setMemoryDisplacement32(random.nextInt());
			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return false; // todo: figure this out
			}
		}
	}

	sealed interface Uncounted extends ExplicitOperand {
		void setRandom(RandomGenerator random, Instruction instruction, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException;


		record SaeControl() implements Uncounted {
			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException {
				instruction.setSuppressAllExceptions(random.nextBoolean());
			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return true;
			}
		}

		record Mask(boolean zeroing) implements Uncounted {
			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException {
				instruction.setOpMask(rp.selectRegister(RegisterSet.MASK, random));
				if (zeroing)
					instruction.setZeroingMasking(random.nextBoolean());
			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return !partition.allowedRegisters().intersection(RegisterSet.MASK).isEmpty();
			}
		}

		record EmbeddedRoundingControl() implements Uncounted {
			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, ResourcePartition rp) throws BlockGenerator.NoPossibilitiesException {
				instruction.setRoundingControl(random.nextInt(5));
			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return true;
			}
		}
	}

	sealed interface SuppressedOperand extends Operand {
		record StatusFlags(StatusFlag flag) implements SuppressedOperand {
			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return partition.statusFlags().contains(flag);
			}
		}

		record Reg(int register) implements SuppressedOperand {
			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return partition.allowedRegisters().hasRegister(register);
			}
		}

		record Mem() implements SuppressedOperand {
			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return false; // disable these for now (TODO)
			}
		}
	}
}
