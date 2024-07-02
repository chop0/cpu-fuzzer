package ax.xz.fuzz;

import ax.xz.fuzz.tester.saved_state;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.util.stream.Stream.concat;

// TODO: include segment registers
public record CPUState(GeneralPurposeRegisters gprs, VectorRegisters zmm, MMXRegisters mmx, long rflags) {
	private static final MemoryLayout ZMM = MemoryLayout.sequenceLayout(64, JAVA_BYTE);


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


	record GeneralPurposeRegisters(long rax, long rbx, long rcx, long rdx, long rsi, long rdi, long rbp, long r8,
								   long r9, long r10, long r11, long r12, long r13, long r14, long r15, long rsp) {
		static GeneralPurposeRegisters filledWith(long thing) {
			return new GeneralPurposeRegisters(thing, thing, thing, thing, thing, thing, thing, thing, thing, thing, thing, thing, thing, thing, thing, thing);
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
			saved_state.rbp(savedState, rsp);
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

	record VectorRegisters(byte[][] zmm) {
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

		void toArray(MemorySegment savedState) {
			for (int i = 0; i < zmm.length; i++) {
				savedState.asSlice(i, ZMM).copyFrom(MemorySegment.ofArray(zmm[i]));
			}
		}

		BigInteger get(int index) {
			return new BigInteger(zmm[index]);
		}

		@Override
		public String toString() {
			return IntStream.range(0, zmm.length)
					.mapToObj(i -> "zmm" + i + "=" + get(i).toString(16))
					.reduce((a, b) -> a + ", " + b)
					.orElse("");
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (!(obj instanceof VectorRegisters vr)) return false;
			return Arrays.deepEquals(zmm, vr.zmm);
		}
	}

	record MMXRegisters(long[] mm) {
		static MMXRegisters filledWith(long thing) {
			var mm = new long[8];
			Arrays.fill(mm, thing);
			return new MMXRegisters(mm);
		}

		static MMXRegisters ofArray(MemorySegment array) {
			return new MMXRegisters(
					StreamSupport.stream(array.spliterator(MemoryLayout.sequenceLayout(8, JAVA_LONG)), false)
							.mapToLong(ms -> ms.get(JAVA_LONG, 0))
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
