package ax.xz.fuzz.x86.arch;

import ax.xz.fuzz.x86.operand.Operand;
import ax.xz.fuzz.instruction.StatusFlag;
import ax.xz.fuzz.x86.operand.Flags;
import ax.xz.fuzz.x86.operand.ImplicitReg;
import com.github.icedland.iced.x86.ConditionCode;
import com.github.icedland.iced.x86.Instruction;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static java.util.stream.Stream.concat;

public class InstructionReference {
	private static final Map<String, Operand[]> suppressedOperands;

	static {
		var dbf = DocumentBuilderFactory.newDefaultInstance();
		Document doc = null;
		try {
			doc = dbf.newDocumentBuilder().parse(InstructionReference.class.getClassLoader().getResourceAsStream("instructions.xml"));
		} catch (SAXException | ParserConfigurationException | IOException e) {
			throw new RuntimeException(e);
		}

		var statusFlags = mnemonicToOperands(doc, InstructionReference::statusFlags);
		var registers = mnemonicToOperands(doc, InstructionReference::registers);
		var registersBase = mnemonicToOperands(doc, InstructionReference::registersBase);

		var s = new HashMap<String, Operand[]>();
		var keys = new HashSet<>(statusFlags.keySet());
		keys.addAll(registers.keySet());

		for (var mnemonic : keys) {
			Set<? extends Operand> flags = statusFlags.getOrDefault(mnemonic, Set.of());
			Set<? extends Operand> regs = registers.getOrDefault(mnemonic, Set.of());
			Set<? extends Operand> regsBase = registersBase.getOrDefault(mnemonic, Set.of());
			var all = concat(regsBase.stream(), concat(flags.stream(), regs.stream())).toArray(Operand[]::new);
			s.put(mnemonic, all);
		}

		s.put("ZEROUPPER", x86RegisterBanks.ZMM_AVX512.stream().map(ImplicitReg::new).toArray(Operand[]::new));
		s.put("ZEROALL", x86RegisterBanks.ZMM_AVX512.stream().map(ImplicitReg::new).toArray(Operand[]::new));
		s.put("SETGE", s.get("SETNL"));

		suppressedOperands = Collections.unmodifiableMap(s);
		System.out.println("Loaded " + suppressedOperands.size() + " suppressed operands");
	}

	private static <T extends Operand> Map<String, Set<T>> mnemonicToOperands(Document doc, Function<Node, Set<T>> mapper) {
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

	public static Operand[] suppressedOperands(Instruction instruction, String mnemonic) {
		mnemonic = normaliseConditionalInstruction(instruction, mnemonic);
		mnemonic = stripMnemonic(mnemonic);

		return suppressedOperands.get(mnemonic);
	}

	private static String stripMnemonic(String mnemonic) {
		return mnemonic.replaceAll("\\{[a-z0-9]*\\}", "")
				.replaceAll("64|32|16|8", "")
				.replaceAll("B|W|D|Q$", "")
				.replaceAll("^[VR]", "")
				.replace(" ", "");
	}

	private static String mnemonic(Node instruction) {
		return stripMnemonic(instruction.getAttributes().getNamedItem("asm").getNodeValue());
	}

	private static Set<Flags> statusFlags(Node instruction) {
		return stream(instruction.getChildNodes())
				.filter(op -> op instanceof Element)
				.filter(op -> op.getAttributes().getNamedItem("suppressed") != null)
				.flatMap(op -> stream(op.getAttributes()))
				.map(Node::getNodeName)

				.filter(name -> name.startsWith("flag_"))
				.map(name -> name.split("_")[1])
				.map(StatusFlag::valueOf)

				.map(Flags::new)
				.collect(toSet());
	}


	public static Set<ImplicitReg> registers(Node instruction) {
		return stream(instruction.getChildNodes())
				.filter(op -> op instanceof Element)
				.filter(op -> op.getAttributes().getNamedItem("suppressed") != null)
				.map(Node::getTextContent)
				.filter(c -> c != null && !c.isBlank())
				.flatMap(n -> Arrays.stream(n.split(",")))
				.map(n -> {
					var id = x86RegisterDescriptor.fromString(n);
					if (id == null)
						throw new NullPointerException(n + " not found");
					return id;
				})
			.flatMap(n -> x86RegisterBanks.getAssociatedRegisters(n).stream())
				.map(ImplicitReg::new)
				.collect(toSet());
	}

	public static Set<ImplicitReg> registersBase(Node instruction) {
		return stream(instruction.getChildNodes())
			.filter(op -> op instanceof Element)
			.filter(op -> op.getAttributes().getNamedItem("suppressed") != null)
			.map(node -> ((Element) node).getAttribute("base"))
			.filter(c -> !c.isBlank())
			.map(n -> {
				var id = x86RegisterDescriptor.fromString(n);
				if (id == null)
					throw new NullPointerException(n + " not found");
				return id;
			})
			.flatMap(n -> x86RegisterBanks.getAssociatedRegisters(n).stream())
			.map(ImplicitReg::new)
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
