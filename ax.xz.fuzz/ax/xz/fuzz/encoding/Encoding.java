package ax.xz.fuzz.encoding;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.EnumNamingStrategies;
import com.fasterxml.jackson.databind.annotation.EnumNaming;

public enum Encoding {
	@JsonAlias("3DNOW")
	D3NOW,
	DISP16_32_64,
	EVEX,
	IS4,
	JIMM16_32_32,
	JIMM32,
	JIMM8,
	MASK,
	MODRM_REG,
	MODRM_RM,
	MVEX,
	NDSNDD,
	OPCODE,
	SIMM16_32_32,
	SIMM16_32_64,
	SIMM8,
	UIMM16,
	UIMM32,
	UIMM8,
	VEX,
	XOP;
}
