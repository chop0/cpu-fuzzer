package ax.xz.fuzz.riscv;

import ax.xz.fuzz.instruction.InstructionBuilder;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

public class RiscvInstructionBuilder implements InstructionBuilder {
	private final RiscvArchitecture architecture;

	private final RiscvOpcode opcode;
	private final Set<RiscvInstructionField> presetFields;
	private final Map<RiscvInstructionField, Integer> fieldValues;

	public RiscvInstructionBuilder(RiscvArchitecture architecture, RiscvOpcode opcode, Set<RiscvInstructionField> presetFields, Map<RiscvInstructionField, Integer> fieldValues) {
		this.architecture = architecture;
		this.opcode = opcode;
		this.presetFields = presetFields;
		this.fieldValues = fieldValues;
	}

	public int encodeInt(long pc) {
		int instruction = 0;
		for (RiscvInstructionField field : opcode.format().fields()) {
			if (!fieldValues.containsKey(field)) {
				throw new IllegalStateException("Field " + field + " is not set");
			}

			instruction = field.apply(instruction, fieldValues.get(field));
		}

		return instruction;
	}

	@Override
	public byte[] encode(long pc) {
		return ByteBuffer.allocate(4).order(LITTLE_ENDIAN).putInt(encodeInt(pc)).array();
	}

	public static RiscvInstructionBuilder of(RiscvArchitecture architecture, RiscvOpcode opcode) {
		var fieldValues = new HashMap<>(opcode.fieldConstraints());

		for (RiscvInstructionField field : opcode.format().fields()) {
			if (!fieldValues.containsKey(field)) {
				fieldValues.put(field, null);
			}
		}

		return new RiscvInstructionBuilder(architecture, opcode, opcode.fieldConstraints().keySet(), fieldValues);
	}

	public static RiscvInstructionBuilder data(RiscvArchitecture architecture, byte[] data) {
		return new RiscvInstructionBuilder(architecture, null, Set.of(), Map.of()) {
			@Override
			public byte[] encode(long pc) {
				return data;
			}

			@Override
			public RiscvInstructionBuilder setField(RiscvInstructionField field, int value) {
				throw new UnsupportedOperationException("Data instructions do not have fields");
			}

			@Override
			public Set<RiscvInstructionField> unsetFields() {
				return Set.of();
			}

			@Override
			public RiscvArchitecture architecture() {
				return super.architecture();
			}
		};
	}

	public RiscvInstructionBuilder setField(RiscvInstructionField field, int value) {
		if (!fieldValues.containsKey(field)) {
			throw new IllegalArgumentException("Field " + field + " is not present in the opcode");
		}

		fieldValues.put(field, value);
		return this;
	}

	public RiscvOpcode opcode() {
		return opcode;
	}

	public Set<RiscvInstructionField> unsetFields() {
		var unsetFields = new HashSet<>(opcode.format().fields());
		unsetFields.removeAll(fieldValues.keySet());
		return unsetFields;
	}

	public RiscvArchitecture architecture() {
		return architecture;
	}
}
