package ax.xz.fuzz.x86.arch;

import ax.xz.fuzz.arch.BranchDescription;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.github.icedland.iced.x86.asm.CodeAssembler;
import com.github.icedland.iced.x86.asm.CodeLabel;

import java.util.function.BiConsumer;

public enum X86BranchDescription implements BranchDescription {
	JO(CodeAssembler::jo),
	JNO(CodeAssembler::jno),
	JS(CodeAssembler::js),
	JNS(CodeAssembler::jns),
	JE(CodeAssembler::je),
	JNE(CodeAssembler::jne),
	JB(CodeAssembler::jb),
	JNB(CodeAssembler::jnb),
	JBE(CodeAssembler::jbe),
	JA(CodeAssembler::ja),
	JL(CodeAssembler::jl),
	JGE(CodeAssembler::jge),
	JLE(CodeAssembler::jle),
	JG(CodeAssembler::jg),
	JP(CodeAssembler::jp),
	JNP(CodeAssembler::jnp),
	JMP(CodeAssembler::jmp);;
	public final BiConsumer<CodeAssembler, CodeLabel> perform;

	X86BranchDescription(BiConsumer<CodeAssembler, CodeLabel> perform) {
		this.perform = perform;
	}

	@Override
	public String asAssembler(int takenIndex, int notTakenIndex) {
		return """
			%s block_%d
			jmp block_%d
			""".formatted(this, takenIndex, notTakenIndex);
	}

	@JsonCreator
	public static X86BranchDescription fromString(String name) {
		return valueOf(name);
	}
}
