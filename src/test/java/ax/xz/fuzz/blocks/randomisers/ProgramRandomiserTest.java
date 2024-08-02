//package ax.xz.fuzz.blocks.randomisers;
//
//import ax.xz.fuzz.instruction.ResourcePartition;
//import ax.xz.fuzz.runtime.Config;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.RepeatedTest;
//
//import java.lang.foreign.Arena;
//import java.util.Random;
//
//import static ax.xz.fuzz.blocks.randomisers.ProgramRandomiser.MEMORY_GRANULARITY;
//import static org.junit.jupiter.api.Assertions.*;
//
//class ProgramRandomiserTest {
//	ProgramRandomiser programRandomiser = new ProgramRandomiser(Config.defaultConfig(), ResourcePartition.all(true));
//	ReverseRandomGenerator rrng;
//
//	private final ResourcePartition rp = ResourcePartition.all(true, Arena.ofAuto().allocate(4096, MEMORY_GRANULARITY));
//	private final Random rng = new Random(0);
//
//	@BeforeEach
//	void setUp() {
//		rrng = ReverseRandomGenerator.create();
////		rng.setSeed(0);
//	}
//
//	@RepeatedTest(5)
//	void reverseTestCase() {
//		var tc = programRandomiser.selectTestCase(rng);
//		programRandomiser.reverseTestCase(rrng, tc);
//		var playbackRng = rrng.flip();
//		var tc2 = programRandomiser.selectTestCase(playbackRng);
//
//		assertEquals(tc, tc2);
//	}
//
//	@RepeatedTest(5)
//	void reverseCPUState() {
//		var cs = programRandomiser.selectCPUState(rng);
//		programRandomiser.reverseCPUState(rrng, cs);
//		var playbackRng = rrng.flip();
//		var cs2 = programRandomiser.selectCPUState(playbackRng);
//
//		assertEquals(cs, cs2);
//	}
//
//	@RepeatedTest(5)
//	void reverseInterestingValue() {
//		var iv = programRandomiser.selectInterestingValue(rng);
//		programRandomiser.reverseInterestingValue(rrng, iv);
//		var playbackRng = rrng.flip();
//		var iv2 = programRandomiser.selectInterestingValue(playbackRng);
//
//		assertEquals(iv, iv2);
//	}
//
//	@RepeatedTest(5)
//	void reverseSequence() {
//		var pairs = programRandomiser.selectBlockPairs(rng, 5);
//		var branches = programRandomiser.selectBranches(rng, 5);
//
//		var seq = programRandomiser.selectSequence(rng, pairs, branches);
//		programRandomiser.reverseSequence(rrng, pairs, branches, seq);
//		var playbackRng = rrng.flip();
//		var seq2 = programRandomiser.selectSequence(playbackRng, pairs, branches);
//
//		assertEquals(seq, seq2);
//	}
//
//	@RepeatedTest(5)
//	void reverseInterleaved() {
//		var pair = programRandomiser.selectBlockPair(rng);
//
//		var seq = programRandomiser.selectInterleaved(rng, pair);
//		programRandomiser.reverseInterleaved(rrng, pair, seq);
//		var playbackRng = rrng.flip();
//		var seq2 = programRandomiser.selectInterleaved(playbackRng, pair);
//
//		assertEquals(seq, seq2);
//	}
//
//	@RepeatedTest(5)
//	void reverseBlockPairs() {
//		var pairs = programRandomiser.selectBlockPairs(rng, 5);
//		programRandomiser.reverseBlockPairs(rrng, pairs);
//		var playbackRng = rrng.flip();
//		var pairs2 = programRandomiser.selectBlockPairs(playbackRng, 5);
//
//		assertArrayEquals(pairs, pairs2);
//	}
//
//	@RepeatedTest(5)
//	void reverseBranches() {
//		var branches = programRandomiser.selectBranches(rng, 5);
//		programRandomiser.reverseBranches(rrng, branches);
//		var playbackRng = rrng.flip();
//		var branches2 = programRandomiser.selectBranches(playbackRng, 5);
//
//		assertArrayEquals(branches, branches2);
//	}
//
//	@RepeatedTest(5)
//	void reverseBranch() {
//		var branch = programRandomiser.selectBranch(rng, 5);
//		programRandomiser.reverseBranch(rrng, branch);
//		var playbackRng = rrng.flip();
//		var branch2 = programRandomiser.selectBranch(playbackRng, 5);
//
//		assertEquals(branch, branch2);
//	}
//
//	@RepeatedTest(5)
//	void reverseBlockPair() {
//		var pair = programRandomiser.selectBlockPair(rng);
//		programRandomiser.reverseBlockPair(rrng, pair);
//		var playbackRng = rrng.flip();
//		var pair2 = programRandomiser.selectBlockPair(playbackRng);
//
//		assertEquals(pair, pair2);
//	}
//
//	@RepeatedTest(5)
//	void reverseResourceSplit() {
//		var rs = programRandomiser.selectResourceSplit(rng);
//		programRandomiser.reverseResourceSplit(rrng, rs.getKey(), rs.getValue());
//		var playbackRng = rrng.flip();
//		var rs2 = programRandomiser.selectResourceSplit(playbackRng);
//
//		assertEquals(rs, rs2);
//	}
//
//	@RepeatedTest(5)
//	void reverseMemorySplit() {
//		var ms = programRandomiser.selectMemorySplit(rng);
//		programRandomiser.reverseMemorySplit(rrng, ms.getKey(), ms.getValue());
//		var playbackRng = rrng.flip();
//		var ms2 = programRandomiser.selectMemorySplit(playbackRng);
//
//		assertEquals(ms, ms2);
//	}
//
//	@RepeatedTest(5)
//	void reverseBlock() {
//		var block = programRandomiser.selectBlock(rng, rp);
//		programRandomiser.reverseBlock(rrng, rp, block);
//		var playbackRng = rrng.flip();
//		var block2 = programRandomiser.selectBlock(playbackRng, rp);
//
//		assertEquals(block, block2);
//	}
//
//	@RepeatedTest(5)
//	void reverseBlockEntry() {
//		var entry = programRandomiser.selectBlockEntry(rng, rp);
//		programRandomiser.reverseBlockEntry(rrng, rp, entry);
//		var playbackRng = rrng.flip();
//		var entry2 = programRandomiser.selectBlockEntry(playbackRng, rp);
//
//		assertEquals(entry, entry2);
//	}
//}