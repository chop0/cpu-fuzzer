package ax.xz.fuzz.blocks;

import ax.xz.fuzz.instruction.Opcode;
import ax.xz.fuzz.instruction.ResourcePartition;
import ax.xz.fuzz.runtime.CPUState;
import ax.xz.fuzz.runtime.ExecutionResult;
import ax.xz.fuzz.runtime.Tester;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
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
import static ax.xz.fuzz.runtime.MemoryUtils.mmap;
import static com.github.icedland.iced.x86.Code.*;

public record OpcodeCache(int version, Opcode[] opcodes) {
	private static final Set<Integer> blacklistedOpcodes = Set.of(
		INCSSPD_R32, INCSSPQ_R64, RDSSPD_R32, RDSSPQ_R64, RSTORSSP_M64, SAVEPREVSSP, SETSSBSY,
		WRSSD_M32_R32, WRSSQ_M64_R64, WRUSSD_M32_R32, WRUSSQ_M64_R64,
		XSAVE_MEM, XSAVES_MEM, XSAVEC_MEM, XSAVE64_MEM, XSAVEC64_MEM, XSAVES64_MEM,
		LSL_R16_RM16, LSL_R32_R32M16, LSL_R64_R64M16,
		CLZEROD, CLZEROW, CLZEROQ,
		CLUI, STUI,
		XGETBV,
		RDPKRU,WRPKRU,
		RDSEED_R16, RDSEED_R32, RDSEED_R64,
		RDRAND_R16, RDRAND_R32, RDRAND_R64,
		RDTSC, RDTSCP, RDPMC,
		XRSTOR_MEM, XRSTORS_MEM, XRSTOR64_MEM, XRSTORS64_MEM,
		RDPID_R32, RDPID_R64, RDPRU,
		XSAVEOPT_MEM, XSAVEOPT64_MEM);
	private static final List<String> disallowedPrefixes = List.of("BT", "BND", "CCS", "MVEX", "KNC", "VIA", "XOP");

	private static final int OPCODES_CACHE_VERSION = blacklistedOpcodes.hashCode() ^ disallowedPrefixes.hashCode();
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

	public static ObjectMapper getMapper() {
		var mapper = new ObjectMapper();
		mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
		mapper.activateDefaultTyping(BasicPolymorphicTypeValidator.builder()
			.allowIfSubType(new BasicPolymorphicTypeValidator.TypeMatcher() {
				@Override
				public boolean match(MapperConfig<?> config, Class<?> clazz) {
					return clazz.isSealed();
				}
			}).build(), ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE);

		return mapper;
	}

	private static Opcode[] loadCachedOpcodes() {
		try {
			var mapper = getMapper();
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
			var mapper = getMapper();

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
		if (insn.isStackInstruction() || insn.isPrivileged() || insn.getFlowControl() != FlowControl.NEXT || insn.isJccShortOrNear())
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
			var rp = ResourcePartition.all(true, scratch);
			var insn = opcode.configureRandomly(new Random(0), rp);

			var result = Tester.runBlock(CPUState.filledWith(scratch.address()), new BasicBlock(List.of(new Block.BlockEntry(rp, opcode, insn, List.of()))));
			return !(result instanceof ExecutionResult.Fault.Sigill);
		} catch (Block.UnencodeableException e) {
			return false;
		}
	}
}