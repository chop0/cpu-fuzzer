package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.BasicBlock;
import ax.xz.fuzz.blocks.Block;
import ax.xz.fuzz.blocks.randomisers.ProgramRandomiser;
import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.RegisterSet;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.instruction.StatusFlag;
import com.github.icedland.iced.x86.Instruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.icedland.iced.x86.Code.SERIALIZE;
import static org.junit.jupiter.api.Assertions.*;

class TesterTest {
	private Tester tester;

	@BeforeEach
	void setUp() {
		tester = Tester.create(Config.defaultConfig(), RegisterSet.ALL_EVEX, StatusFlag.all());
		var rand = new ProgramRandomiser(Config.defaultConfig(), tester.masterPartition);
		var rng = new SplittableRandom();
		rand.selectTestCase(rng); // warm up
	}

	@Test
	void profile() {
		var rand = new ProgramRandomiser(Config.defaultConfig(), tester.masterPartition);
		var rng = new SplittableRandom();
		rand.selectTestCase(rng);

		var end = Instant.now().plus(Duration.ofSeconds(30));
		while (Instant.now().isBefore(end)) {
			rand.selectTestCase(rng);
		}
	}

	@Test
	void minimise() {

	}

	@Test
	void runBlock() throws Block.UnencodeableException {
		var rng = new Random(0);
		var state = CPUState.random(rng);
		var result = Tester.runBlock(state, new BasicBlock(List.of()));
		assertInstanceOf(ExecutionResult.Success.class, result);
		assertEquals(state, ((ExecutionResult.Success) result).state());
	}

	@Test
	void runBlockFault() throws Block.UnencodeableException {
		var rng = new Random(0);
		var state = CPUState.random(rng);
		var result = Tester.runBlock(state, new BasicBlock(List.of(new Block.BlockEntry(
			ResourcePartition.all(true),
			Opcode.of(SERIALIZE, "SERIALIZE"),
			Instruction.create(SERIALIZE),
			List.of()))));
		assertInstanceOf(ExecutionResult.Fault.Sigill.class, result);
	}

	@Test
	void multithreadedRunBlock() throws InterruptedException {
		var executor = Executors.newThreadPerTaskExecutor(Thread.ofPlatform().factory());
		var futures = new ArrayBlockingQueue<Future<?>>(96);
		for (int i = 0; i < 95; i++) {
			futures.add(executor.submit(() -> {
				try {
					runBlock();
					runBlockFault();
				} catch (Block.UnencodeableException e) {
					throw new RuntimeException(e);
				}
			}));
		}

		executor.shutdown();
		assertTrue(executor.awaitTermination(1000, TimeUnit.MILLISECONDS));

		for (Future<?> future : futures) {
			assertTrue(future.isDone());
			assertDoesNotThrow(() -> future.get());
		}
	}

	@Test
	void testRunBlock() {
	}
}