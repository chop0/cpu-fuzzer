package ax.xz.fuzz.runtime.state;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.util.StdConverter;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static java.lang.foreign.MemoryLayout.sequenceLayout;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public record VectorRegisters(
	@JsonSerialize(contentConverter = StringZmm.class)
	@JsonDeserialize(contentConverter = ZmmString.class)
	byte[][] zmm) {
	private static final MemoryLayout ZMM = sequenceLayout(64, JAVA_BYTE);

	public VectorRegisters {
		if (zmm == null) {
			zmm = new byte[32][64];
		} else if (zmm.length < 32) {
			var newZmm = new byte[32][64];
			System.arraycopy(zmm, 0, newZmm, 0, zmm.length);
			for (int i = zmm.length; i < 32; i++) {
				newZmm[i] = new byte[64];
			}
			zmm = newZmm;
		}
	}

	public List<RegisterDifference> diff(VectorRegisters other) {
		var differences = new ArrayList<RegisterDifference>();
		for (int i = 0; i < zmm.length; i++) {
			if (!Arrays.equals(zmm[i], other.zmm[i])) {
				differences.add(new RegisterDifference("zmm" + i, i, zmm[i], other.zmm[i]));
			}
		}
		return differences;
	}

	public VectorRegisters withZeroed(int index) {
		var newZmm = Arrays.copyOf(zmm, zmm.length);
		newZmm[index] = new byte[64];
		return new VectorRegisters(newZmm);
	}

	public VectorRegisters withZeroed(int start, int end) {
		var newZmm = Arrays.copyOf(zmm, zmm.length);
		for (int i = start; i < end; i++) {
			newZmm[i] = new byte[64];
		}
		return new VectorRegisters(newZmm);
	}

	static VectorRegisters filledWith(long thing) {
		var zmm = new byte[32][64];
		for (byte[] bytes : zmm) {
			for (int i = 0; i < 64; i += 8) {
				ByteBuffer.wrap(bytes).putLong(i, thing);
			}
		}

		return new VectorRegisters(zmm);
	}

	static VectorRegisters ofArray(MemorySegment savedState) {
		return new VectorRegisters(
			StreamSupport.stream(savedState.spliterator(ZMM), false)
				.map(ms -> ms.toArray(JAVA_BYTE))
				.toArray(byte[][]::new)
		);
	}

	static VectorRegisters random(RandomGenerator rng) {
		var zmm = new byte[32][64];
		for (byte[] bytes : zmm) {
			rng.nextBytes(bytes);
		}
		return new VectorRegisters(zmm);
	}

	void toArray(MemorySegment savedState) {
		for (int i = 0; i < zmm.length; i++) {
			savedState.asSlice(i * ZMM.byteSize(), ZMM).copyFrom(MemorySegment.ofArray(zmm[i]));
		}
	}

	BigInteger get(int index) {
		return new BigInteger(zmm[index]);
	}

	String getAsString(int index) {
		var builder = new StringBuilder();
		boolean hasNonZero = false;
		for (int i = 0; i < 64; i++) {
			builder.append(String.format("%02x", zmm[index][i]));
			hasNonZero |= zmm[index][i] != 0;
		}
		if (!hasNonZero) {
			return "0";
		}
		return builder.toString();
	}

	@Override
	public String toString() {
		return IntStream.range(0, zmm.length)
			.mapToObj(i -> "zmm" + i + "=" + getAsString(i))
			.reduce((a, b) -> a + ", " + b)
			.orElse("");
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof VectorRegisters vr)) return false;

		for (int i = 0; i < zmm.length; i++) {
			if (!Arrays.equals(zmm[i], vr.zmm[i])) {
				return false;
			}
		}

		return true;
	}

	private static class ZmmString extends StdConverter<String, byte[]> {
		@Override
		public byte[] convert(String value) {
			var bytes = new byte[64];
			for (int i = 0; i < 64; i += 2) {
				if (i+1 >= value.length()) {
					break;
				}
				bytes[i] = (byte) Integer.parseInt(value.substring(i, i + 2), 16);
			}
			return bytes;
		}
	}

	private static class StringZmm extends StdConverter<byte[], String> {
		@Override
		public String convert(byte[] value) {
			var builder = new StringBuilder();
			for (byte b : value) {
				builder.append(String.format("%02x", b));
			}
			return builder.toString();
		}
	}
}
