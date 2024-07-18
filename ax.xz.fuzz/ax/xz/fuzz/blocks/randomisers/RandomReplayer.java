package ax.xz.fuzz.blocks.randomisers;

import java.util.ArrayDeque;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static java.util.Objects.requireNonNull;

public class RandomReplayer {
	private final ArrayDeque<Object> numbers;
	private boolean flipped = false;

	public RandomReplayer() {
		this.numbers = new ArrayDeque<>();
	}
	
	public RandomGenerator flip() {
		if (flipped)
			throw new IllegalStateException("Already flipped");
		
		flipped = true;
		return new ReplayRng();
	}

	public void push(boolean value) {
		if (flipped)
			throw new IllegalStateException("Cannot push boolean after flipping");

		numbers.add(value);
	}

	public void push(Number value) {
		if (flipped)
			throw new IllegalStateException("Cannot push number after flipping");

		numbers.add(requireNonNull(value));
	}
	
	private class ReplayRng implements RandomGenerator {
		private <T> T poll() {
			return (T) numbers.remove();
		}
		
		@Override
		public boolean nextBoolean() {
			return poll();
		}

		@Override
		public void nextBytes(byte[] bytes) {
			for (int i = 0; i < bytes.length; i++)
				bytes[i] = poll();
		}

		@Override
		public float nextFloat() {
			return poll();
		}

		@Override
		public float nextFloat(float bound) {
			return poll();
		}

		@Override
		public float nextFloat(float origin, float bound) {
			return poll();
		}

		@Override
		public double nextDouble() {
			return poll();
		}

		@Override
		public double nextDouble(double bound) {
			return poll();
		}

		@Override
		public double nextDouble(double origin, double bound) {
			return poll();
		}

		@Override
		public int nextInt() {
			return poll();
		}

		@Override
		public int nextInt(int bound) {
			return poll();
		}

		@Override
		public int nextInt(int origin, int bound) {
			return poll();
		}

		@Override
		public long nextLong() {
			return poll();
		}

		@Override
		public long nextLong(long bound) {
			return poll();
		}

		@Override
		public long nextLong(long origin, long bound) {
			return poll();
		}

		@Override
		public double nextGaussian() {
			throw new UnsupportedOperationException();
		}

		@Override
		public double nextGaussian(double mean, double stddev) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double nextExponential() {
			throw new UnsupportedOperationException();
		}


		@Override
		public boolean isDeprecated() {
			throw new UnsupportedOperationException();
		}

		@Override
		public DoubleStream doubles() {
			throw new UnsupportedOperationException();
		}

		@Override
		public DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
			throw new UnsupportedOperationException();
		}

		@Override
		public DoubleStream doubles(long streamSize) {
			throw new UnsupportedOperationException();
		}

		@Override
		public DoubleStream doubles(long streamSize, double randomNumberOrigin, double randomNumberBound) {
			throw new UnsupportedOperationException();
		}

		@Override
		public DoubleStream equiDoubles(double left, double right, boolean isLeftIncluded, boolean isRightIncluded) {
			throw new UnsupportedOperationException();
		}

		@Override
		public IntStream ints() {
			throw new UnsupportedOperationException();
		}

		@Override
		public IntStream ints(int randomNumberOrigin, int randomNumberBound) {
			throw new UnsupportedOperationException();
		}

		@Override
		public IntStream ints(long streamSize) {
			throw new UnsupportedOperationException();
		}

		@Override
		public IntStream ints(long streamSize, int randomNumberOrigin, int randomNumberBound) {
			throw new UnsupportedOperationException();
		}

		@Override
		public LongStream longs() {
			throw new UnsupportedOperationException();
		}

		@Override
		public LongStream longs(long randomNumberOrigin, long randomNumberBound) {
			throw new UnsupportedOperationException();
		}

		@Override
		public LongStream longs(long streamSize) {
			throw new UnsupportedOperationException();
		}

		@Override
		public LongStream longs(long streamSize, long randomNumberOrigin, long randomNumberBound) {
			throw new UnsupportedOperationException();
		}
	}
}
