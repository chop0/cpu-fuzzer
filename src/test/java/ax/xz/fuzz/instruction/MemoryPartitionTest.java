package ax.xz.fuzz.instruction;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.*;

class MemoryPartitionTest {

	@Test
	void isEmpty() {
		var mp = new MemoryPartition();
		assertTrue(mp.isEmpty());
		mp = new MemoryPartition(MemorySegment.NULL);
		assertTrue(mp.isEmpty());

		mp = new MemoryPartition(MemorySegment.ofArray(new byte[1]));
		assertFalse(mp.isEmpty());
	}

	@Test
	void byteSize() {
		var mp = new MemoryPartition();
		assertEquals(0, mp.byteSize());
		mp = new MemoryPartition(MemorySegment.NULL);
		assertEquals(0, mp.byteSize());

		mp = new MemoryPartition(MemorySegment.ofArray(new byte[1]));
		assertEquals(1, mp.byteSize());
	}

	@Test
	void contains() {
		var mp = new MemoryPartition();
		assertFalse(mp.contains(0, 50));
		var seg = Arena.ofAuto().allocate(50);

		mp = new MemoryPartition(seg);
		assertTrue(mp.contains(seg.address(), 23));
		assertFalse(mp.contains(seg.address() + 50, 1));
		assertTrue(mp.contains(seg.address() + 49, 1));
	}

	@Test
	void canFulfil() {
	}

	@Test
	void union() {
	}
}