package ax.xz.fuzz;

import com.github.icedland.iced.x86.ConditionCode;
import com.github.icedland.iced.x86.Instruction;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static java.util.stream.Stream.concat;

public class InstructionReference {
	private static final Map<String, Operand.SuppressedOperand[]> suppressedOperands;

	static {
		var dbf = DocumentBuilderFactory.newDefaultInstance();
		Document doc = null;
		try {
			doc = dbf.newDocumentBuilder().parse(new File("instructions.xml"));
		} catch (SAXException | ParserConfigurationException | IOException e) {
			throw new RuntimeException(e);
		}

		var statusFlags = mnemonicToOperands(doc, InstructionReference::statusFlags);
		var registers = mnemonicToOperands(doc, InstructionReference::registers);

		var s = new HashMap<String, Operand.SuppressedOperand[]>();
		var keys = new HashSet<>(statusFlags.keySet());
		keys.addAll(registers.keySet());

		for (var mnemonic : keys) {
			Set<? extends Operand.SuppressedOperand> flags = statusFlags.getOrDefault(mnemonic, Set.of());
			Set<? extends Operand.SuppressedOperand> regs = registers.getOrDefault(mnemonic, Set.of());
			var all = concat(flags.stream(), regs.stream()).toArray(Operand.SuppressedOperand[]::new);
			s.put(mnemonic, all);
		}

		s.put("ZEROUPPER", RegisterSet.ZMM_EVEX.stream().mapToObj(Operand.SuppressedOperand.Reg::new).toArray(Operand.SuppressedOperand[]::new));
		s.put("ZEROALL", RegisterSet.ZMM_EVEX.stream().mapToObj(Operand.SuppressedOperand.Reg::new).toArray(Operand.SuppressedOperand[]::new));

		suppressedOperands = Collections.unmodifiableMap(s);
	}

	private static <T extends Operand.SuppressedOperand> Map<String, Set<T>> mnemonicToOperands(Document doc, Function<Node, Set<T>> mapper) {
		return stream(doc.getElementsByTagName("instruction"))
				.unordered()
				.collect(groupingByConcurrent(
						InstructionReference::mnemonic,
						reducing(new HashSet<>(), mapper, (a, b) -> {
							var c = new HashSet<>(a);
							c.addAll(b);
							return c;
						})
				));
	}

	private static <K, V> Map<K, V> mapOf(K key, V value, Object... alteratingsKeysAndValues) {
		Map<K, V> map = new LinkedHashMap<K, V>();
		map.put(key, value);
		for (int i = 0; i < alteratingsKeysAndValues.length; i += 2)
			map.put((K) alteratingsKeysAndValues[i],
					(V) alteratingsKeysAndValues[i + 1]);
		return map;
	}


	private static String normaliseConditionalInstruction(Instruction instruction, String mnemonic) {
		return switch (instruction.getConditionCode()) {
			case ConditionCode.E -> mnemonic.replaceAll("E$", "Z");
			case ConditionCode.NE -> mnemonic.replaceAll("NE$", "NZ");
			case ConditionCode.B -> mnemonic.replaceAll("NAE$", "B").replaceAll("C$", "B");
			case ConditionCode.AE -> mnemonic.replaceAll("AE$", "NB").replaceAll("NC$", "NB");
			case ConditionCode.BE -> mnemonic.replaceAll("NA$", "BE");
			case ConditionCode.A -> mnemonic.replaceAll("A$", "NBE");
			case ConditionCode.L -> mnemonic.replaceAll("NGE$", "L");
			case ConditionCode.GE -> mnemonic.replaceAll("GE$", "NL");
			case ConditionCode.LE -> mnemonic.replaceAll("NG$", "LE");
			case ConditionCode.G -> mnemonic.replaceAll("G$", "NLE");
			case ConditionCode.P -> mnemonic.replaceAll("PE$", "P");
			case ConditionCode.NP -> mnemonic.replaceAll("PO$", "NP");
			default -> mnemonic;
		};
	}

	public static Operand.SuppressedOperand[] suppressedOperands(Instruction instruction, String mnemonic) {
		mnemonic = normaliseConditionalInstruction(instruction, mnemonic);
		mnemonic = stripMnemonic(mnemonic);

		var result = suppressedOperands.get(mnemonic);
		if (result == null)
			return null;

		return result != null ? result : new Operand.SuppressedOperand[0];
	}

	private static String stripMnemonic(String mnemonic) {
		return mnemonic.replaceAll("\\{[a-z0-9]*\\}", "")
				.replaceAll("64|32|16|8", "")
				.replaceAll("B|W|D|Q$", "")
				.replaceAll("^(V|R)", "")
				.replace(" ", "");
	}

	private static String mnemonic(Node instruction) {
		return stripMnemonic(instruction.getAttributes().getNamedItem("asm").getNodeValue());
	}

	private static Set<Operand.SuppressedOperand.StatusFlags> statusFlags(Node instruction) {
		return stream(instruction.getChildNodes())
				.filter(op -> op instanceof Element)
				.filter(op -> op.getAttributes().getNamedItem("suppressed") != null)
				.flatMap(op -> stream(op.getAttributes()))
				.map(Node::getNodeName)

				.filter(name -> name.startsWith("flag_"))
				.map(name -> name.split("_")[1])
				.map(StatusFlag::valueOf)

				.map(Operand.SuppressedOperand.StatusFlags::new)
				.collect(toSet());
	}


	public static Set<Operand.SuppressedOperand.Reg> registers(Node instruction) {
		return stream(instruction.getChildNodes())
				.filter(op -> op instanceof Element)
				.filter(op -> op.getAttributes().getNamedItem("suppressed") != null)
				.map(Node::getTextContent)
				.filter(c -> c != null && !c.isBlank())
				.flatMap(n -> Arrays.stream(n.split(",")))
				.map(Registers::byName)
				.filter(Objects::nonNull)
				.map(Operand.SuppressedOperand.Reg::new)
				.collect(toSet());
	}

	private static Stream<Node> stream(NodeList nl) {
		return IntStream.range(0, nl.getLength())
				.mapToObj(nl::item);
	}

	private static Stream<Node> stream(NamedNodeMap nl) {
		return IntStream.range(0, nl.getLength())
				.mapToObj(nl::item);
	}
}
