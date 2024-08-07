package ax.xz.fuzz.arch;

import ax.xz.fuzz.instruction.RegisterDescriptor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static ax.xz.fuzz.arch.Architecture.getArchitecture;

@JsonSerialize(using = CPUState.CPUStateSerializer.class)
@JsonDeserialize(using = CPUState.CPUStateDeserializer.class)

public record CPUState( Map<RegisterDescriptor, byte[]> values) {
	public List<RegisterDifference> diff(CPUState other) {
		var differences = new ArrayList<RegisterDifference>();

		for (var entry : values.entrySet()) {
			var register = entry.getKey();
			var valueA = entry.getValue();
			var valueB = other.values.get(register);

			if (!Arrays.equals(valueA, valueB)) {
				differences.add(new RegisterDifference(register, valueA, valueB));
			}
		}

		return differences;
	}

	public static CPUState zeroed() {
		return new CPUState(Map.of());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}

		CPUState cpuState = (CPUState) obj;
		for (var entry : values.entrySet()) {
			var register = entry.getKey();
			var valueA = entry.getValue();
			var valueB = cpuState.values.get(register);

			if (!Arrays.equals(valueA, valueB)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		var sorted = values.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().index())).toList();
		var sb = new StringBuilder();
		for (var entry : sorted) {
			sb.append(entry.getKey()).append(": ");
			for (var b : entry.getValue()) {
				sb.append(String.format("%02x", b));
			}
			sb.append("\n");
		}

		return sb.toString();
	}

	public static class CPUStateSerializer extends JsonSerializer<CPUState> {

		@Override
		public void serialize(CPUState cpuState, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
			Map<RegisterDescriptor, byte[]> sortedValues = cpuState.values.entrySet().stream()
				.sorted(Map.Entry.comparingByKey(Comparator.comparingInt(RegisterDescriptor::index)))
				.collect(Collectors.toMap(
					Map.Entry::getKey,
					Map.Entry::getValue,
					(e1, e2) -> e1,
					LinkedHashMap::new
				));

			jsonGenerator.writeStartObject();
			for (Map.Entry<RegisterDescriptor, byte[]> entry : sortedValues.entrySet()) {
				jsonGenerator.writeFieldName(entry.getKey().toString());
				jsonGenerator.writeString(bytesToHex(entry.getValue()));
			}
			jsonGenerator.writeEndObject();
		}

		private String bytesToHex(byte[] bytes) {
			StringBuilder sb = new StringBuilder();
			for (byte b : bytes) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		}
	}


	public static class CPUStateDeserializer extends JsonDeserializer<CPUState> {

		@Override
		public CPUState deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
			JsonNode rootNode = jsonParser.getCodec().readTree(jsonParser);
			Map<RegisterDescriptor, byte[]> values = new HashMap<>();

			Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> field = fields.next();
				RegisterDescriptor rd = getArchitecture().registerByName(field.getKey());

				if (!getArchitecture().trackedRegisters().hasRegister(rd))
					continue;

				byte[] data = hexStringToByteArray(field.getValue().asText());
				values.put(rd, data);
			}

			return new CPUState(values);
		}

		private byte[] hexStringToByteArray(String s) {
			int len = s.length();
			byte[] data = new byte[len / 2];
			for (int i = 0; i < len; i += 2) {
				data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
			}
			return data;
		}
	}

}
