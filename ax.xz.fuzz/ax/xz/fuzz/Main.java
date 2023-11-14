package ax.xz.fuzz;

import java.lang.foreign.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

import static ax.xz.fuzz.TestCase.randomBranch;
import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.Register.R15;
import static java.time.Instant.now;

public class Main {
	private static final int NUM_BLOCKS = 5;


	public static void main(String[] args) throws BlockGenerator.NoPossibilitiesException, BasicBlock.UnencodeableException {
		try (var arena = Arena.ofConfined()) {
			var scratch1 = mmap(MemorySegment.NULL, 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(),
					MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0)
					.reinterpret(4096, arena, ms -> munmap(ms, 4096));
			if (scratch1.address() == MAP_FAILED().address())
				throw new RuntimeException("mmap failed");

			var scratch2 = mmap(MemorySegment.NULL, 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(),
					 MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0)
					.reinterpret(4096, arena, ms -> munmap(ms, 4096));
			if (scratch2.address() == MAP_FAILED().address())
				throw new RuntimeException("mmap failed");

			var code = mmap(MemorySegment.NULL, 4096 * 16, PROT_READ() | PROT_WRITE() | PROT_EXEC(), MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0)
					.reinterpret(4096 * 16, arena, ms -> munmap(ms, 4096 * 16));
			if (code.address() == MAP_FAILED().address())
				throw new RuntimeException("mmap failed");

			var rng = new Random(1);

			var partitions = ResourcePartition.partitioned(true, rng, scratch1, scratch2);

			var lhsGenerator = new BlockGenerator(partitions[0].withAllowedRegisters(partitions[0].allowedRegisters().subtract(RegisterSet.of(R15))));
			var rhsGenerator = new BlockGenerator(partitions[1].withAllowedRegisters(partitions[1].allowedRegisters().subtract(RegisterSet.of(R15))));

			int numFaulted = 0;
			int succeeded = 0;

			Instant start = now();
			Instant lastPrint = start;

			for (; ; ) {
				partitions = ResourcePartition.partitioned(true, rng, scratch1, scratch2);
				lhsGenerator.setPartition(partitions[0].withAllowedRegisters(partitions[0].allowedRegisters().subtract(RegisterSet.getAssociatedRegisterSet(R15))));
				rhsGenerator.setPartition(partitions[1].withAllowedRegisters(partitions[1].allowedRegisters().subtract(RegisterSet.getAssociatedRegisterSet(R15))));

				var lhs = new BasicBlock[NUM_BLOCKS];
				var rhs = new BasicBlock[NUM_BLOCKS];

				for (int i = 0; i < lhs.length; i++) {
					lhs[i] = lhsGenerator.createBasicBlock(rng);
					rhs[i] = rhsGenerator.createBasicBlock(rng);
				}

				var branches = new TestCase.Branch[lhs.length];
				for (int i = 0; i < branches.length; i++) {
					branches[i] = new TestCase.Branch(randomBranch(rng), rng.nextInt(0, lhs.length + 1), rng.nextInt(0, lhs.length + 1));
				}

				ExecutionResult previousResult = null;
				TestCase previousBlock = null;
				for (int i = 0; i < 2; i++) {
					scratch1.fill((byte) 0);
					scratch2.fill((byte) 0);

					var block = new InterleavedBlock[NUM_BLOCKS];
					for (int j = 0; j < block.length; j++) {
						block[j] = BasicBlock.randomlyInterleaved(rng, lhs[j], rhs[j]);
					}

					var buf = code.asByteBuffer();
					var test = new TestCase(block, branches);
					test.encode(code.address(), buf::put, R15, 100);

					var result = Tester.runBlock(CPUState.filledWith(0), code.asSlice(0, buf.position()));

					if (result instanceof ExecutionResult.Fault) numFaulted++;
					else succeeded++;

					if (now().isAfter(lastPrint.plusSeconds(1))) {
						System.out.println("Faulted: " + numFaulted + " Succeeded: " + succeeded);
						System.out.println("Rate: " + (numFaulted + succeeded) / (Duration.between(start, now()).toMillis() / 1000d) + " per second");
						lastPrint = now();
					}

					if (previousResult != null &&
						(
								(previousResult instanceof ExecutionResult.Fault) != (result instanceof ExecutionResult.Fault)
								|| (previousResult instanceof ExecutionResult.Success && !previousResult.equals(result))
						)
					) {
						System.err.println("Found inconsistent behaviour");
						System.err.println("--- LHS ----");
						System.out.println(partitions[0]);

						System.err.println(lhs);
						System.err.println("--- RHS ----");
						System.out.println(partitions[1]);
						System.err.println(rhs);
						System.err.println("--- First interleaved test ----");
						System.err.println(previousBlock);
						System.err.println("--- Second interleaved test ----");
						System.err.println(test);
						System.err.println("--- First result ----");
						System.err.println(previousResult);
						System.err.println("--- Second result ----");
						System.err.println(result);

//						Minimiser.minimise(() -> {
//							scratch1.fill((byte)0);
//							scratch2.fill((byte)0);
//						}, previousBlock.blocks(), test.blocks(), branches);
						System.exit(0);
					}


					previousResult = result;
					previousBlock = test;
				}
			}
		}
	}
}