package ax.xz.fuzz.riscv;

import ax.xz.fuzz.arch.Architecture;
import ax.xz.fuzz.riscv.base.RiscvBaseModule;

import java.nio.ByteBuffer;

import static ax.xz.fuzz.riscv.base.RiscvBaseRegisters.*;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

public class Main {
	public static void main(String[] args) {
		var arch = new RiscvArchitecture(new RiscvBaseModule.RV64I());

		Architecture.withArchitecture(arch, () -> {
			var assembler = new RiscvAssembler(0, arch);
			var start = assembler.label();
			var loopHeader = assembler.label();
			var end = assembler.label();

			assembler.bind(start);
			assembler.addi(x1, x0, 64);

			assembler.bind(loopHeader);
			assembler.addi(x1, x1, -1);
			assembler.beq(x1, x0, end);

			assembler.bind(end);
			assembler.add(x3, x3, x3);
			var output = assembler.assemble();

			var disassemble = new RiscvDisassembler(arch);

			System.out.println(disassemble.disassemble(output));
			System.out.println("asm volatile(");
			for (int i = 0; i < output.length; i += 4) {
				var instructionInt = ByteBuffer.wrap(output, i, 4).order(LITTLE_ENDIAN).getInt();
				System.out.printf("\t\".byte 0x%02x, 0x%02x, 0x%02x, 0x%02x\\n\" // %s %n",
					output[i], output[i + 1], output[i + 2], output[i + 3], disassemble.disassemble(instructionInt));
			}

			System.out.println(");");
		});
	}
}