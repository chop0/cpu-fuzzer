package ax.xz.fuzz.runtime;

import ax.xz.fuzz.tester.saved_state;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.util.StdConverter;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static ax.xz.fuzz.tester.slave_h.*;
import static java.lang.foreign.MemoryLayout.sequenceLayout;
import static java.lang.foreign.MemoryLayout.structLayout;
import static java.lang.foreign.ValueLayout.*;

// TODO: include segment registers
public record CPUState(GeneralPurposeRegisters gprs, VectorRegisters zmm, MMXRegisters mmx, long rflags) {
	private static final MemoryLayout ZMM = sequenceLayout(64, JAVA_BYTE);

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof CPUState that)) return false;

		return gprs.equals(that.gprs)
		       && zmm.equals(that.zmm)
		       && mmx.equals(that.mmx);
	}

	public static CPUState ofSavedState(MemorySegment savedState) {
		return new CPUState(
				GeneralPurposeRegisters.ofSavedState(savedState),
				VectorRegisters.ofArray(saved_state.zmm(savedState)),
				MMXRegisters.ofArray(saved_state.mm(savedState)),
				saved_state.rflags(savedState)
		);
	}

	public void toSavedState(MemorySegment savedState) {
		gprs.toSavedState(savedState);
		zmm.toArray(saved_state.zmm(savedState));
		mmx.toArray(saved_state.mm(savedState));
		saved_state.rflags(savedState, rflags);
	}

	public static CPUState filledWith(long thing) {
		return new CPUState(
				GeneralPurposeRegisters.filledWith(thing),
				VectorRegisters.filledWith(thing),
				MMXRegisters.filledWith(thing),
				0
		);
	}

	public static CPUState random(RandomGenerator rng) {
		return new CPUState(
				GeneralPurposeRegisters.random(rng),
				VectorRegisters.random(rng),
				MMXRegisters.random(rng),
				0
		);
	}


	public record GeneralPurposeRegisters(long rax, long rbx, long rcx, long rdx, long rsi, long rdi, long rbp, long r8,
								   long r9, long r10, long r11, long r12, long r13, long r14, long r15, long rsp) {
		private static final RecordComponent[] components = GeneralPurposeRegisters.class.getRecordComponents();
		private static final Constructor<GeneralPurposeRegisters> constructor = (Constructor)GeneralPurposeRegisters.class.getConstructors()[0];

		public GeneralPurposeRegisters withRsp(long newRsp) {
			return new GeneralPurposeRegisters(rax, rbx, rcx, rdx, rsi, rdi, rbp, r8, r9, r10, r11, r12, r13, r14, r15, newRsp);
		}

		public GeneralPurposeRegisters withZeroed(int idx) {
			var parameters = new Long[components.length];
			for (int i = 0; i < components.length; i++) {
				if (i == idx) {
					parameters[i] = 0L;
				} else {
					try {
						parameters[i] = (long)components[i].getAccessor().invoke(this);
					} catch (IllegalAccessException | InvocationTargetException e) {
						throw new RuntimeException(e);
					}
				}
			}

			try {
				return constructor.newInstance((Object[]) parameters);
			} catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof GeneralPurposeRegisters that)) return false;

			return r8 == that.r8
			       && r9 == that.r9
			       && rax == that.rax
			       && rbx == that.rbx
			       && rcx == that.rcx
			       && rdx == that.rdx
			       && rsi == that.rsi
			       && rdi == that.rdi
			       && rbp == that.rbp
			       && r10 == that.r10
			       && r11 == that.r11
			       && r12 == that.r12
			       && r13 == that.r13
			       && r14 == that.r14
			       && r15 == that.r15
			       && rsp == that.rsp;
		}

		static GeneralPurposeRegisters filledWith(long thing) {
			return new GeneralPurposeRegisters(thing, thing, thing, thing, thing, thing, thing, thing, thing, thing, thing, thing, thing, thing, thing, thing);
		}

		static GeneralPurposeRegisters random(RandomGenerator rng) {
			return new GeneralPurposeRegisters(
					rng.nextLong(), rng.nextLong(), rng.nextLong(), rng.nextLong(),
					rng.nextLong(), rng.nextLong(), rng.nextLong(), rng.nextLong(),
					rng.nextLong(), rng.nextLong(), rng.nextLong(), rng.nextLong(),
					rng.nextLong(), rng.nextLong(), rng.nextLong(), rng.nextLong()
			);
		}

		static GeneralPurposeRegisters ofSavedState(MemorySegment savedState) {
			return new GeneralPurposeRegisters(
					saved_state.rax(savedState),
					saved_state.rbx(savedState),
					saved_state.rcx(savedState),
					saved_state.rdx(savedState),
					saved_state.rsi(savedState),
					saved_state.rdi(savedState),
					saved_state.rbp(savedState),
					saved_state.r8(savedState),
					saved_state.r9(savedState),
					saved_state.r10(savedState),
					saved_state.r11(savedState),
					saved_state.r12(savedState),
					saved_state.r13(savedState),
					saved_state.r14(savedState),
					saved_state.r15(savedState),
					saved_state.rsp(savedState)
			);
		}

		void toSavedState(MemorySegment savedState) {
			saved_state.rax(savedState, rax);
			saved_state.rbx(savedState, rbx);
			saved_state.rcx(savedState, rcx);
			saved_state.rdx(savedState, rdx);
			saved_state.rsi(savedState, rsi);
			saved_state.rdi(savedState, rdi);
			saved_state.rbp(savedState, rbp);
			saved_state.r8(savedState, r8);
			saved_state.r9(savedState, r9);
			saved_state.r10(savedState, r10);
			saved_state.r11(savedState, r11);
			saved_state.r12(savedState, r12);
			saved_state.r13(savedState, r13);
			saved_state.r14(savedState, r14);
			saved_state.r15(savedState, r15);
			saved_state.rsp(savedState, rsp);
		}
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

	public record VectorRegisters(
		@JsonSerialize(contentConverter = StringZmm.class)
			@JsonDeserialize(contentConverter = ZmmString.class)
		byte[][] zmm) {
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
	}

	public record MMXRegisters(long[] mm) {
		public MMXRegisters {
			if (mm.length != 8) {
				throw new IllegalArgumentException("MMX registers must have 8 elements");
			}
		}

		MMXRegisters withZeroed(int index) {
			var newMm = Arrays.copyOf(mm, mm.length);
			newMm[index] = 0;
			return new MMXRegisters(newMm);
		}

		static MMXRegisters filledWith(long thing) {
			var mm = new long[8];
			Arrays.fill(mm, thing);
			return new MMXRegisters(mm);
		}

		static MMXRegisters random(RandomGenerator rng) {
			var mm = new long[8];
			for (int i = 0; i < mm.length; i++) {
				mm[i] = rng.nextLong();
			}
			return new MMXRegisters(mm);
		}

		static MMXRegisters ofArray(MemorySegment array) {
			return new MMXRegisters(
					StreamSupport.stream(array.spliterator(JAVA_LONG_UNALIGNED), false)
							.mapToLong(ms -> ms.get(JAVA_LONG_UNALIGNED, 0))
							.toArray()
			);
		}

		void toArray(MemorySegment array) {
			for (int i = 0; i < mm.length; i++) {
				array.setAtIndex(JAVA_LONG, i, mm[i]);
			}
		}

		@Override
		public String toString() {
			return IntStream.range(0, mm.length)
					.mapToObj(i -> "mm" + i + "=" + Long.toUnsignedString(mm[i]))
					.reduce((a, b) -> a + ", " + b)
					.orElse("");
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (!(obj instanceof MMXRegisters mmx)) return false;
			return Arrays.equals(mm, mmx.mm);
		}
	}

}
