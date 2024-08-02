package ax.xz.fuzz.blocks;

import ax.xz.fuzz.runtime.ExecutableSequence;
import com.github.icedland.iced.x86.asm.CodeAssembler;

import java.util.*;
import java.util.stream.Collectors;

public class InterleavedBlock implements Block {
	private final Block lhs, rhs;

	private final BitSet picks;

	private final int[] lhsIndices, rhsIndices; // indices[original_idx] contains the index of the item in the interleaved block
	private final int[] indices; // indices[interleaved_idx] contains the index of the item in the original lhs/rhs block

	public InterleavedBlock(Block lhs, Block rhs, BitSet picks) {
		this.lhs = lhs;
		this.rhs = rhs;
		this.picks = picks;

		this.indices = new int[lhs.size() + rhs.size()];
		this.lhsIndices = new int[lhs.size()];
		this.rhsIndices = new int[rhs.size()];

		int lhsIndex = 0, rhsIndex = 0;
		for (int i = 0; i < (lhs.size() + rhs.size()); i++) {
			if (picks.get(i)) {
				lhsIndices[lhsIndex] = i;
				indices[i] = lhsIndex++;
			} else {
				rhsIndices[rhsIndex] = i;
				indices[i] = rhsIndex++;
			}
		}
	}

	@Override
	public int size() {
		return lhs.size() + rhs.size();
	}

	@Override
	public SequencedCollection<BlockEntry> items() {
		return new ItemSequence(false);
	}

	public int leftInterleavedIndex(int lhsIndex) {
		return lhsIndices[lhsIndex];
	}

	public int rightInterleavedIndex(int rhsIndex) {
		return rhsIndices[rhsIndex];
	}

	public Block lhs() {
		return lhs;
	}

	public Block rhs() {
		return rhs;
	}

	public BitSet picks() {
		return picks;
	}

	private class ItemSequence implements SequencedCollection<BlockEntry> {
		private final boolean reverse;

		private ItemSequence(boolean reverse) {
			this.reverse = reverse;
		}

		@Override
		public SequencedCollection<BlockEntry> reversed() {
			return new ItemSequence(!reverse);
		}

		@Override
		public int size() {
			return indices.length;
		}

		@Override
		public boolean isEmpty() {
			return indices.length == 0;
		}

		@Override
		public boolean contains(Object o) {
			if (!(o instanceof BlockEntry entry)) {
				return false;
			}

			for (var item : this) {
				if (item == entry || item.equals(entry)) {
					return true;
				}
			}

			return false;
		}

		@Override
		public Iterator<BlockEntry> iterator() {
			return new Iterator<>() {
				private final Iterator<BlockEntry> lhsIter, rhsIter;
				private int position = reverse ? indices.length - 1 : 0;

				{
					if (reverse) {
						lhsIter = lhs.items().reversed().iterator();
						rhsIter = rhs.items().reversed().iterator();
					} else {
						lhsIter = lhs.items().iterator();
						rhsIter = rhs.items().iterator();
					}
				}

				@Override
				public boolean hasNext() {
					return position >= 0 && position < indices.length;
				}

				@Override
				public BlockEntry next() {
					if (!hasNext()) {
						throw new NoSuchElementException();
					}

					var result = picks.get(position) ? lhsIter.next()  :rhsIter.next() ;
					position += reverse ? -1 : 1;
					return result;
				}
			};
		}

		@Override
		public BlockEntry[] toArray() {
			var result = new BlockEntry[indices.length];
			int i = 0;
			for (var item : this) {
				result[i++] = item;
			}
			return result;
		}

		@Override
		public <T> T[] toArray(T[] a) {
			if (a.length < indices.length) {
				a = Arrays.copyOf(a, indices.length);
			}

			int i = 0;
			for (var item : this) {
				a[i++] = (T) item;
			}

			return a;
		}

		@Override
		public boolean add(BlockEntry blockEntry) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			for (var o : c) {
				if (!contains(o)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public boolean addAll(Collection<? extends BlockEntry> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();
		for (var item : items()) {
			byte[] bytes = null;
			try {
				bytes = item.encode(0);
			} catch (UnencodeableException e) {
				throw new RuntimeException(e);
			}

			sb.append("new byte[]{");
			for (var b : bytes) {
				sb.append(String.format("(byte) 0x%02x, ", b));
			}
			sb.append("},\n");
		}

		return sb.toString();
	}



	@Override
	public Block without(int... instructionIndex) {
		var skipIndices = Arrays.stream(instructionIndex).boxed().collect(Collectors.toSet());

		var lhsSkip = new ArrayList<Integer>();
		var rhsSkip = new ArrayList<Integer>();
		var newPicks = new BitSet(lhs.size() + rhs.size());
		int picksIndex = 0;

		for (int i = 0; i < lhs.size() + rhs.size(); i++) {
			if (!skipIndices.contains(i)) {
				newPicks.set(picksIndex++, picks.get(i));
			}
		}

		for (var i : skipIndices) {
			if (picks.get(i)) {
				lhsSkip.add(indices[i]);
			} else {
				rhsSkip.add(indices[i]);
			}
		}

		var newLhs = lhs.without(lhsSkip.stream().mapToInt(i -> i).toArray());
		var newRhs = rhs.without(rhsSkip.stream().mapToInt(i -> i).toArray());

		return new InterleavedBlock(newLhs, newRhs, newPicks);
	}

	@Override
	public final boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof InterleavedBlock that)) return false;

		return lhs.equals(that.lhs) && rhs.equals(that.rhs) && Arrays.equals(lhsIndices, that.lhsIndices) && Arrays.equals(rhsIndices, that.rhsIndices) && Arrays.equals(indices, that.indices);
	}

	@Override
	public int hashCode() {
		int result = lhs.hashCode();
		result = 31 * result + rhs.hashCode();
		result = 31 * result + Arrays.hashCode(lhsIndices);
		result = 31 * result + Arrays.hashCode(rhsIndices);
		result = 31 * result + Arrays.hashCode(indices);
		return result;
	}
}
