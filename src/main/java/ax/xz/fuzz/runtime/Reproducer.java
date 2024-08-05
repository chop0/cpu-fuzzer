package ax.xz.fuzz.runtime;

import ax.xz.fuzz.blocks.Block;
import ax.xz.fuzz.blocks.BlockEntry;
import ax.xz.fuzz.instruction.Registers;

import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ax.xz.fuzz.runtime.RecordedTestCase.disassemble;


public class Reproducer {
	private static final String blockPrologue = """
		\t"xor rcx, rcx \\n"
		\t"cmp rcx, 100 \\n"
		\t"jae branch_%d \\n"
		""";
	public String generateCReproducer(RecordedTestCase rtc, int... registers) throws Block.UnencodeableException {
		var sequenceA = new ExecutableSequence(rtc.blocksA(), rtc.branches());
		var sequenceB = new ExecutableSequence(rtc.blocksB(), rtc.branches());

		var stateStruct = cStateStruct(registers);
		var printFunction = structPrintFunction(registers);

		var first = toAsm(sequenceA, "repro_1", registers);
		var second = toAsm(sequenceB, "repro_2", registers);

		return """
			#include <stdint.h>
			#include <stdio.h>
			
			%s
			%s
			%s
			%s
			""".formatted(stateStruct, printFunction, first, second);
	}

	public String toAsm(ExecutableSequence sequence, String functionName, int... savedRegisters) throws Block.UnencodeableException {
		return """
			__attribute__((naked))
			void %s(register_record_t *record1) {
			%s
			}
			""".formatted(functionName, indent(toAsm0(sequence, savedRegisters), 1));
	}

	private String toAsm0(ExecutableSequence sequence, int... savedRegisters) throws Block.UnencodeableException {
		var sb = new StringBuilder();

		sb.append("""
			asm volatile (
				"mov [rsp - 8], rbx \\n"
				"mov [rsp - 16], rbp \\n"
				"mov [rsp - 24], r12 \\n"
				"mov [rsp - 32], r13 \\n"
				"mov [rsp - 40], r14 \\n"
				"mov [rsp - 48], r15 \\n"
			
				"mov rcx, 0 \\n"
			""");

		Block[] blocks = sequence.blocks();
		for (int i = 0; i < blocks.length; i++) {
			var block = blocks[i];
			sb.append(indent(toAsm(block, sequence.branches()[i], i, blocks.length), 1)).append("\n");
		}

		sb.append("\n\t\"branch_").append(blocks.length).append(":").append("\\n\"\n");
		sb.append(");\n\n");

		sb.append(saveInterestingRegisters(savedRegisters));

		sb.append("""
		asm volatile (
			"mov rbx, [rsp - 8] \\n"
			"mov rbp, [rsp - 16] \\n"
			"mov r12, [rsp - 24] \\n"
			"mov r13, [rsp - 32] \\n"
			"mov r14, [rsp - 40] \\n"
			"mov r15, [rsp - 48] \\n"
			
			"ret \\n"
		);
			""");

		return sb.toString();
	}

	private String indent(String s, int level) {
		return s.lines().map(l -> "\t".repeat(level) + l).reduce((a, b) -> a + "\n" + b).orElseThrow();
	}

	private String toAsm(Block block, Branch branch, int i, int max) throws Block.UnencodeableException {
		var lines = block.items().stream().map(this::assembleLine).map(n -> "\t" + n).collect(Collectors.joining("\n"));

		return """
			"branch_%d:\\n"
			%s
			%s
			\t"%s branch_%d\\n"
			\t"jmp branch_%d\\n"
			
			""".formatted(i, blockPrologue.formatted(max), lines, branch.type().name().toLowerCase(), branch.takenIndex(), branch.notTakenIndex());
	}

	private String assembleLine(BlockEntry item) {
		byte[] b = null;
		try {
			b = item.encode(0);
		} catch (Block.UnencodeableException e) {
			throw new RuntimeException(e);
		}
		var rawBytes = asUnsignedInt(b).mapToObj(i -> String.format("0x%02x", i)).collect(Collectors.joining(", "));
		int maxLength = 15 * "0x00, ".length();

		int padding = maxLength - rawBytes.length();
		var paddingStr = " ".repeat(padding);

		return "\".byte " + rawBytes + " \\n\"" + paddingStr + " // " + disassemble(b);
	}

	private IntStream asUnsignedInt(byte[] item) {
		var intArray = new int[item.length];
		for (int i = 0; i < item.length; i++) {
			intArray[i] = item[i] & 0xFF;
		}

		return IntStream.of(intArray);
	}

	public static String cStateStruct(int... registers) {
		var elements = new ArrayList<String>();

		for (int i = 0; i < registers.length; i++) {
			var name = Registers.byValue(registers[i]).toLowerCase();
			int size = getRegisterSize(registers[i]);

			elements.add("\tuint8_t " + name + "[" + size + "];");
		}

		return """
			typedef struct {
			%s
			} register_record_t;
			
			register_record_t test_case_output = { 0 };
			""".formatted(String.join("\n", elements));
	}

	private static int getRegisterSize(int index) {
		return switch (Registers.byValue(index).toLowerCase().replaceAll("[^a-z]", "")) {
			case String s when s.startsWith("e") -> 4;
			case String s when s.startsWith("r") -> 8;
			case "xmm" -> 16;
			case "ymm" -> 32;
			case "zmm" -> 64;
			case "mm" -> 8;
			default -> throw new IllegalArgumentException("Unknown register type: " + Registers.byValue(index).toLowerCase());
		};
	}

	private static String saveInterestingRegisters(int... registers) {
		var moves = new ArrayList<String>();
		var constraints = new ArrayList<String>();

		for (int i = 0; i < registers.length; i++) {
			var name = Registers.byValue(registers[i]).toLowerCase();
			var baseInstruction = switch (name.replaceAll("[^a-z]", "")) {
				case String s when s.startsWith("e") -> "mov";
				case String s when s.startsWith("r") -> "mov";
				case "xmm", "ymm", "zmm" -> "vmovups";
				case "mm" -> "mov";
				default -> throw new IllegalArgumentException("Unknown register type: " + name);
			};
			moves.add("\t" + '"' + baseInstruction + " %" + i + ", " + name + "\\n" + '"');
			constraints.add("\"m\"(test_case_output." + name + ")");
		}

		return """
			asm volatile (
			%s
				: %s
			);
			
			""".formatted(String.join("\n", moves), String.join(", ", constraints));
	}

	public static String structPrintFunction(int... registers) {
		var elements = new ArrayList<String>();

		for (int i = 0; i < registers.length; i++) {
			var name = Registers.byValue(registers[i]).toLowerCase();
			elements.add("""
				printf("%1$-4s: ");
				for (int i = 0; i < sizeof(record1->%1$s); i++) {
					printf("%%02x ", record1->%1$s[i]);
				}
				printf("\\n");
				""".formatted(name));

			elements.add("""
				printf("%1$-4s: ");
				for (int i = 0; i < sizeof(record2->%1$s); i++) {
					printf("%%02x ", record2->%1$s[i]);
				}
				printf("\\n");
				""".formatted(name));
		}

		return """
			void print_register_record(register_record_t *record1, register_record_t *record2) {
			%s
			}
			""".formatted(elements.stream().flatMap(String::lines).map(s -> "\t" + s).collect(Collectors.joining("\n")));
	}
}
