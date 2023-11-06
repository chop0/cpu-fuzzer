package ax.xz.fuzz;

import com.github.icedland.iced.x86.Code;

import java.lang.foreign.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

import static ax.xz.fuzz.tester.slave_h.*;
import static java.time.Instant.now;

public class Main {


	public static void main(String[] args) throws InstructionGenerator.NoPossibilitiesException {
		// void do_test(uint8_t *code, size_t code_length, struct execution_result *output)

		var scratch1 = mmap(MemorySegment.ofAddress(0x10000000), 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(),
				MAP_FIXED() | MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0).asSlice(0, 0);
		if (scratch1.address() == MAP_FAILED().address())
			throw new RuntimeException("mmap failed");

		var scratch2 = mmap(MemorySegment.ofAddress(0x20000000), 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(),
				MAP_FIXED() | MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0).asSlice(0, 0);
		if (scratch2.address() == MAP_FAILED().address())
			throw new RuntimeException("mmap failed");


		var rng = new Random();
		var partitions = ResourcePartition.partitioned(false, rng, scratch1, scratch2);

		var lhsGenerator = new InstructionGenerator(false, partitions[0]);
		var rhsGenerator = new InstructionGenerator(false, partitions[1]);

		int numFaulted = 0;
		int succeeded = 0;

		Instant start = now();
		Instant lastPrint = start;

		for (; ; ) {
			try {
				 partitions = ResourcePartition.partitioned(false, rng, scratch1, scratch2);
				lhsGenerator.setPartition(partitions[0]);
				rhsGenerator.setPartition(partitions[1]);

				var lhs = lhsGenerator.createBasicBlock(rng);
				var rhs = rhsGenerator.createBasicBlock(rng);

				ExecutionResult previousResult = null;
				CombinedBlock previousBlock = null;
				for (int i = 0; i < 2; i++) {
					scratch1.fill((byte)0);
					scratch2.fill((byte)0);

					var block = CombinedBlock.randomlyInterleaved(rng, lhs, rhs);
					var result = Tester.runBlock(CPUState.filledWith(0), block);

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
						System.err.println("--- First interleaved block ----");
						System.err.println(previousBlock);
						System.err.println("--- Second interleaved block ----");
						System.err.println(block);
						System.err.println("--- First result ----");
						System.err.println(previousResult);
						System.err.println("--- Second result ----");
						System.err.println(result);

						Minimiser.minimise(() -> {
							scratch1.fill((byte)0);
							scratch2.fill((byte)0);
						}, previousBlock, block);
						System.exit(0);
					}

					if (result instanceof ExecutionResult.Fault fault && fault instanceof ExecutionResult.Fault.Sigill) {
						var faultOpcode = Tester.findInstructionOpcode(block, fault.address());
						if (faultOpcode != null) {
							lhsGenerator.handleUnencodeable(faultOpcode);
							rhsGenerator.handleUnencodeable(faultOpcode);
							System.out.println("Disabled opcode " + faultOpcode);
						}
					}

					previousResult = result;
					previousBlock = block;
				}
			} catch (CombinedBlock.UnencodeableException e) {
				lhsGenerator.handleUnencodeable(e.opcode);
				rhsGenerator.handleUnencodeable(e.opcode);
				System.out.println("Disabled opcode 2 " + e.opcode);
			}
		}
	}
}