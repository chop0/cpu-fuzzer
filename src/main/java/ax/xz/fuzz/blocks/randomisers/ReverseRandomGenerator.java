package ax.xz.fuzz.blocks.randomisers;

import java.nio.ByteBuffer;
import java.util.random.RandomGenerator;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

public class ReverseRandomGenerator {
	private boolean closed = false;
	private ByteBuffer buffer = ByteBuffer.allocate(64).order(LITTLE_ENDIAN);

	private ReverseRandomGenerator() {
	}

	public static ReverseRandomGenerator create() {
		return new ReverseRandomGenerator();
	}

	public ReverseRandomGenerator pushBoolean(boolean value) {
		ensureOpen();
		ensureCapacity(1);

		buffer.put(value ? (byte) 1 : 0);

		return this;
	}

	public ReverseRandomGenerator pushByte(byte value) {
		ensureOpen();
		ensureCapacity(1);

		buffer.put(value);
		return this;
	}

	public ReverseRandomGenerator pushShort(short value) {
		ensureOpen();
		ensureCapacity(2);

		buffer.putShort(value);
		return this;
	}

	public ReverseRandomGenerator pushInt(int value) {
		ensureOpen();
		ensureCapacity(4);

		buffer.putInt(value);
		return this;
	}

	public ReverseRandomGenerator pushLong(long value) {
		ensureOpen();
		ensureCapacity(8);

		buffer.putLong(value);
		return this;
	}

	public ReverseRandomGenerator pushFloat(float value) {
		ensureOpen();
		ensureCapacity(4);

		buffer.putFloat(value);
		return this;
	}

	public ReverseRandomGenerator pushDouble(double value) {
		ensureOpen();
		ensureCapacity(8);

		buffer.putDouble(value);
		return this;
	}

	public ReverseRandomGenerator push(ByteBuffer value) {
		ensureOpen();
		ensureCapacity(value.remaining());

		buffer.put(value);
		return this;
	}

	public void extend(ReverseRandomGenerator other) {
		other.closed = true;
		push(other.buffer);
		other.buffer = null;
	}

	private void ensureCapacity(int size) {
		ensureOpen();

		if (buffer.remaining() < size) {
			var oldBuffer = buffer;
			oldBuffer.flip();

			buffer = ByteBuffer.allocate(buffer.capacity() * 2).order(LITTLE_ENDIAN);
			buffer.put(oldBuffer);
		}
	}

	private void ensureOpen() {
		if (closed)
			throw new IllegalStateException("Already closed");
	}

	public RandomGenerator flip() {
		ensureOpen();

		closed = true;
		buffer.flip();
		var result = new ReplayRng(buffer);
		buffer = null;
		return result;
	}

	private static class ReplayRng implements RandomGenerator {
		private final ByteBuffer buffer;

		private ReplayRng(ByteBuffer buffer) {
			this.buffer = buffer;
		}

		@Override
		public boolean nextBoolean() {
			return switch (buffer.get()) {
				case 0 -> false;
				case 1 -> true;
				default -> throw new IllegalStateException("Invalid boolean value");
			};
		}

		@Override
		public void nextBytes(byte[] bytes) {
			buffer.get(bytes);
		}

		@Override
		public float nextFloat() {
			return buffer.getFloat();
		}

		@Override
		public float nextFloat(float bound) {
			return buffer.getFloat();
		}

		@Override
		public float nextFloat(float origin, float bound) {
			return buffer.getFloat();
		}

		@Override
		public double nextDouble() {
			return buffer.getDouble();
		}

		@Override
		public double nextDouble(double bound) {
			return buffer.getDouble();
		}

		@Override
		public double nextDouble(double origin, double bound) {
			return buffer.getDouble();
		}

		@Override
		public int nextInt() {
			return buffer.getInt();
		}

		@Override
		public int nextInt(int bound) {
			return buffer.getInt();
		}

		@Override
		public int nextInt(int origin, int bound) {
			return buffer.getInt();
		}

		@Override
		public long nextLong() {
			return buffer.getLong();
		}

		@Override
		public long nextLong(long bound) {
			return buffer.getLong();
		}

		@Override
		public long nextLong(long origin, long bound) {
			return buffer.getLong();
		}
	}
}
