package ax.xz.fuzz.instruction;

import com.github.icedland.iced.x86.Register;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.icedland.iced.x86.Register.*;


public class Registers {

	public static int MXCSR = 256;
	public static int GDTR = 257;
	public static int LDTR = 258;
	public static int IDTR = 259;
	public static int TR = 260;
	public static int MSRS = 261;
	public static int TSC = 262;
	public static int SSP = 263;
	public static int TSCAUX = 264;
	public static int X87CONTROL = 265;
	public static int X87STATUS = 266;
	public static int X87TAG = 267;
	public static int X87PUSH = 268;
	public static int X87POP = 269;
	public static int X87POP2 = 270;
	public static int UIF = 271;
	public static int XCR0 = 272;
	public static int ST0 = 273;
	public static int ST1 = 274;

	private static final Map<String, Integer> registers;
	private static final String[] registerNames;

	static {
		var officialRegisters = Arrays.stream(Register.class.getFields())
				.filter(field -> (field.getModifiers() & (Modifier.FINAL | Modifier.STATIC)) == (Modifier.FINAL | Modifier.STATIC))
				.collect(Collectors.toMap(Field::getName, field -> {
					try {
						return field.getInt(null);
					} catch (IllegalAccessException e) {
						throw new ExceptionInInitializerError(e);
					}
				}));

		var r = new HashMap<>(officialRegisters);
		r.put("MXCSR", MXCSR);
		r.put("GDTR", GDTR);
		r.put("LDTR", LDTR);
		r.put("IDTR", IDTR);
		r.put("TR", TR);
		r.put("MSRS", MSRS);
		r.put("FSBASE", FS);
		r.put("GSBASE", GS);
		r.put("IP", EIP); // TODO: wrong
		r.put("TSC", TSC);
		r.put("SSP", SSP);
		r.put("TSCAUX", TSCAUX);
		r.put("X87CONTROL", X87CONTROL);
		r.put("X87STATUS", X87STATUS);
		r.put("X87TAG", X87TAG);
		r.put("X87PUSH", X87PUSH);
		r.put("X87POP", X87POP);
		r.put("X87POP2", X87POP2);
		r.put("UIF", UIF);
		r.put("XCR0", XCR0);
		r.put("ST(0)", ST0);
		r.put("ST(1)", ST1);


		registers = Collections.unmodifiableMap(r);
		registerNames = new String[registers.size()];
		registers.forEach((name, value) -> registerNames[value] = name);
	}

	public static Integer byName(String name) {
		return registers.get(name);
	}

	public static String byValue(int value) {
		return registerNames[value];
	}
}
