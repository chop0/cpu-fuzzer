//package ax.xz.fuzz;
//
//import com.github.icedland.iced.x86.Register;
//
//import java.lang.foreign.Arena;
//import java.lang.foreign.MemorySegment;
//import java.util.Random;
//
//import static ax.xz.fuzz.tester.slave_h.*;
//import static ax.xz.fuzz.tester.slave_h.MAP_FAILED;
//import static com.github.icedland.iced.x86.Register.R15;
//
//public class BigTest {
//	public static void main(String[] args) throws BlockGenerator.NoPossibilitiesException, CombinedBlock.UnencodeableException {
//		var scratch1 = mmap(MemorySegment.ofAddress(0x10000000), 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(),
//				MAP_FIXED() | MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0).reinterpret(4096);
//		if (scratch1.address() == MAP_FAILED().address())
//			throw new RuntimeException("mmap failed");
//
//		var scratch2 = mmap(MemorySegment.ofAddress(0x20000000), 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(),
//				MAP_FIXED() | MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0).reinterpret(4096);
//		if (scratch2.address() == MAP_FAILED().address())
//			throw new RuntimeException("mmap failed");
//
//		scratch1.fill((byte)0);
//		scratch2.fill((byte)0);
//
//		var rng = new Random(1);
//		var gen = ResourcePartition.partitioned(false, rng, scratch1, scratch2);
//
//		var blocks = new CombinedBlock[5];
//		for (int i = 0; i < 5; i++)
//			blocks[i] = CombinedBlock.randomlyInterleaved(rng, lhsGen.createBasicBlock(rng), rhsGen.createBasicBlock(rng));
//
//		var test = new TestCase(blocks);
//		var seg = Arena.ofAuto().allocate(4096);
//		test.encode(rng, 0x41414000, seg.asByteBuffer()::put, R15, 1000);
//		var result = Tester.runBlock(CPUState.filledWith(0), seg);
//		System.out.println(result);
//	}
//}
