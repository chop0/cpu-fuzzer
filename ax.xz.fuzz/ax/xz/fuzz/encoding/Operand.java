package ax.xz.fuzz.encoding;

import ax.xz.fuzz.blocks.NoPossibilitiesException;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.instruction.StatusFlag;
import com.github.icedland.iced.x86.Instruction;
import com.github.icedland.iced.x86.OpKind;

import java.util.random.RandomGenerator;

import static com.github.icedland.iced.x86.Register.NONE;

public sealed interface Operand {
	boolean fulfilledBy(ResourcePartition partition);

	sealed interface ExplicitOperand extends Operand {
	}

	sealed interface Counted extends ExplicitOperand {
		void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException;

		record Reg(RegisterSet possibleRegisters) implements Counted {
			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
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
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
				instruction.setOpKind(operandIndex, OpKind.MEMORY);
				instruction.setMemoryBase(NONE);
				instruction.setMemoryIndex(NONE);
				instruction.setMemoryDisplSize(4);
				instruction.setMemoryDisplacement64(rp.selectAddress(random, byteSize(), 16, instruction.getMemoryDisplSize()));
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
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
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
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
				instruction.setOpKind(operandIndex, OpKind.REGISTER);
				instruction.setOpRegister(operandIndex, register);
			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return partition.allowedRegisters().hasRegister(register);
			}
		}


		record FixedNumber(byte value) implements Counted {

			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIndex, ResourcePartition rp) throws NoPossibilitiesException {
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
			public void setRandom(RandomGenerator random, Instruction instruction, int operandIdx, ResourcePartition rp) throws NoPossibilitiesException {
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
		void setRandom(RandomGenerator random, Instruction instruction, ResourcePartition rp) throws NoPossibilitiesException;


		record Mask(boolean zeroing) implements Uncounted {
			@Override
			public void setRandom(RandomGenerator random, Instruction instruction, ResourcePartition rp) throws NoPossibilitiesException {
				instruction.setOpMask(rp.selectRegister(RegisterSet.MASK, random));
				if (zeroing)
					instruction.setZeroingMasking(random.nextBoolean());
			}

			@Override
			public boolean fulfilledBy(ResourcePartition partition) {
				return !partition.allowedRegisters().intersection(RegisterSet.MASK).isEmpty();
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
