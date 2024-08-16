package ax.xz.xed;

import java.util.Optional;

import static ax.xz.xed.xed_17.*;
import static ax.xz.xed.xed_5.*;

public enum InstructionStatusFlag {
	of(XED_FLAG_of()),
	sf(XED_FLAG_sf()),
	zf(XED_FLAG_zf()),
	af(XED_FLAG_af()),
	pf(XED_FLAG_pf()),
	cf(XED_FLAG_cf()),
	df(XED_FLAG_df()),
	vif(XED_FLAG_vif()),
	iopl(XED_FLAG_iopl()),
	_if(XED_FLAG_if()),
	ac(XED_FLAG_ac()),
	vm(XED_FLAG_vm()),
	rf(XED_FLAG_rf()),
	nt(XED_FLAG_nt()),
	tf(XED_FLAG_tf()),
	id(XED_FLAG_id()),
	vip(XED_FLAG_vip()),
	fc0(XED_FLAG_fc0()),
	fc1(XED_FLAG_fc1()),
	fc2(XED_FLAG_fc2()),
	fc3(XED_FLAG_fc3()),
	LAST(XED_FLAG_LAST()),
	ACTION_INVALID(XED_FLAG_ACTION_INVALID()),
	ACTION_u(XED_FLAG_ACTION_u()),
	ACTION_tst(XED_FLAG_ACTION_tst()),
	ACTION_mod(XED_FLAG_ACTION_mod()),
	ACTION_0(XED_FLAG_ACTION_0()),
	ACTION_pop(XED_FLAG_ACTION_pop()),
	ACTION_ah(XED_FLAG_ACTION_ah()),
	ACTION_1(XED_FLAG_ACTION_1()),
	ACTION_LAST(XED_FLAG_ACTION_LAST());

	private final int xedEnum;

	InstructionStatusFlag(int xedEnum) {
		this.xedEnum = xedEnum;
	}

	public static Optional<InstructionStatusFlag> from(int xedEnum) {
		if (xedEnum == 0) {
			return Optional.empty();
		}

		var result = values()[xedEnum - 1];
		if (result.xedEnum != xedEnum) {
			throw new AssertionError("mismatched enum: " + xedEnum + ", " + result);
		}

		return Optional.of(result);
	}

	public static InstructionStatusFlag fromString(String name) {
		if (name.equals("if"))
			return _if;

		for (var value : values()) {
			if (value.name().equals(name)) {
				return value;
			}
		}
		throw new IllegalArgumentException("Unknown name: " + name);
	}
}
