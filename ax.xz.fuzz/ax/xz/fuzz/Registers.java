package ax.xz.fuzz;

import com.github.icedland.iced.x86.Register;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.icedland.iced.x86.Register.FS;
import static com.github.icedland.iced.x86.Register.GS;


public class Registers {
	public static RegisterSet SPECIAL = RegisterSet.of(Registers.MXCSR);

	public static int MXCSR = 256, GDTR = 257, LDTR = 258, IDTR = 259, TR = 260, MSRS = 261;

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
