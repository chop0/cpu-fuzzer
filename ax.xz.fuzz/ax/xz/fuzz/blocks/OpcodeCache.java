package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.runtime.CPUState;
import ax.xz.fuzz.runtime.ExecutionResult;
import ax.xz.fuzz.runtime.Tester;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.icedland.iced.x86.Code;
import com.github.icedland.iced.x86.FlowControl;
import com.github.icedland.iced.x86.Instruction;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static ax.xz.fuzz.runtime.MemoryUtils.Protection.READ;
import static ax.xz.fuzz.runtime.MemoryUtils.Protection.WRITE;
import static ax.xz.fuzz.runtime.MemoryUtils.assignPkey;
import static ax.xz.fuzz.runtime.MemoryUtils.mmap;
import static ax.xz.fuzz.runtime.Tester.SCRATCH_PKEY;
import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.Code.*;
import static com.github.icedland.iced.x86.Code.XSAVEOPT64_MEM;

record OpcodeCache(int version, Opcode[] opcodes) {
	private static final Set<Integer> blacklistedOpcodes = Set.of(CLUI, STUI, XGETBV, RDPKRU,WRPKRU, RDSEED_R16, RDSEED_R32, RDSEED_R64, RDTSC, RDTSCP, RDPMC, RDRAND_R16, RDRAND_R32, RDRAND_R64, XRSTOR_MEM, XRSTORS_MEM, XRSTOR64_MEM, XRSTORS64_MEM, RDPID_R32, RDPID_R64, RDPRU, XSAVEOPT_MEM, XSAVEOPT64_MEM);
	private static final List<String> disallowedPrefixes = List.of("BND", "CCS", "MVEX", "KNC", "VIA", "XOP");

	private static final int OPCODES_CACHE_VERSION = 1;
	private static final Path OPCODES_CACHE_PATH = Path.of("opcodes.json");

	public static Opcode[] loadOpcodes() {
		var cachedOpcodes = loadCachedOpcodes();
		if (cachedOpcodes == null) {
			var opcodes = findValidOpcodes();
			saveCachedOpcodes(opcodes);
			return opcodes;
		} else {
			return cachedOpcodes;
		}
	}

	private static Opcode[] loadCachedOpcodes() {
		try {
			var mapper = new ObjectMapper();
			var opcodes = mapper.readValue(OPCODES_CACHE_PATH.toFile(), OpcodeCache.class);
			if (opcodes.version != OPCODES_CACHE_VERSION)
				return null;

			return opcodes.opcodes();
		} catch (Exception e) {
			return null;
		}
	}

	private static void saveCachedOpcodes(Opcode[] opcodes) {
		try {
			var mapper = new ObjectMapper();
			mapper.writeValue(OPCODES_CACHE_PATH.toFile(), new OpcodeCache(OPCODES_CACHE_VERSION, opcodes));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	private static Opcode[] findValidOpcodes() {
		return getOpcodeIDs()
				.filter(OpcodeCache::isOpcodeAllowed)
				.filter(OpcodeCache::doesOpcodeWork)
				.toArray(Opcode[]::new);
	}

	private static Stream<Opcode> getOpcodeIDs() {
		return Arrays.stream(Code.class.getFields())
				.filter(field -> (field.getModifiers() & (Modifier.FINAL | Modifier.STATIC)) == (Modifier.FINAL | Modifier.STATIC))
				.map(field -> {
					int opCode;

					try {
						opCode = field.getInt(null);
					} catch (IllegalAccessException e) {
						throw new ExceptionInInitializerError(e);
					}

					return Opcode.of(opCode, field.getName());
				}).filter(Objects::nonNull);
	}

	private static boolean isOpcodeAllowed(Opcode opcode) {
		var name = opcode.icedFieldName();
		var icedID = opcode.icedVariant();

		if (name.startsWith("F"))
			return false;

		var insn = Instruction.create(icedID);
		if (insn.isStackInstruction() || insn.getFlowControl() != FlowControl.NEXT || insn.isJccShortOrNear())
			return false;

		if (disallowedPrefixes.stream().anyMatch(name::contains))
			return false;

		if (blacklistedOpcodes.contains(opcode.icedVariant())) {
			return false;
		}

		return true;
	}

	private static boolean doesOpcodeWork(Opcode opcode) {
		try (var arena = Arena.ofConfined()) {
			var scratch = mmap(arena, MemorySegment.ofAddress(0x4000000), 4096, READ, WRITE);
			assignPkey(scratch, SCRATCH_PKEY);
			var rp = ResourcePartition.all(true, scratch);
			var insn = opcode.configureRandomly(new Random(0), rp);

			var result = Tester.runBlock(CPUState.filledWith(scratch.address()), new BasicBlock(List.of(new Block.BlockEntry(rp, opcode, insn, List.of()))));
			return !(result instanceof ExecutionResult.Fault.Sigill);
		} catch (Block.UnencodeableException e) {
			return false;
		}
	}
}
