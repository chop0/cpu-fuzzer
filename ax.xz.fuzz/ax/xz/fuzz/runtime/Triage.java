package ax.xz.fuzz.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.icedland.iced.x86.asm.CodeAssembler;
import com.github.icedland.iced.x86.asm.CodeLabel;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import static ax.xz.fuzz.runtime.ExecutableSequence.TEST_CASE_FINISH;
import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.asm.AsmRegisters.*;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class Triage {
	interface CodeBlock {
		void assemble(CodeAssembler assembler);
	}

	private static MemorySegment block(Trampoline trampoline, Branch[] branches, byte[][]... blocks) {
		var seg = mmap(MemorySegment.NULL, 4096, PROT_READ() | PROT_WRITE() | PROT_EXEC(), MAP_PRIVATE() | MAP_ANONYMOUS(), -1, 0)
				.reinterpret(4096, Arena.ofAuto(), ms -> munmap(ms, 4096));
		var assembler = new CodeAssembler(64);

		// 1. our entrypoint
		assembler.xor(r15, r15);
		// exitpoint
		var exit = assembler.createLabel("exit");

		CodeLabel[] blockHeaders = new CodeLabel[blocks.length + 1];
		CodeLabel[] testCaseLocs = new CodeLabel[blocks.length];
		for (int i = 0; i < blockHeaders.length - 1; i++) {
			blockHeaders[i] = assembler.createLabel();
			testCaseLocs[i] = assembler.createLabel();
		}

		blockHeaders[blockHeaders.length - 1] = exit;

		for (int i = 0; i < blocks.length; i++) {
			assembler.label(blockHeaders[i]);

			assembler.cmp(r15, 100);
			assembler.jge(exit);
			assembler.inc(r15);

			for (var item : blocks) {
				if (item == null)
					throw new IllegalArgumentException("instruction must not be null");

				for (var insn : item) {
					assembler.db(insn);
				}
			}

			branches[i].type().perform.accept(assembler, blockHeaders[branches[i].takenIndex()]);
			assembler.jmp(blockHeaders[branches[i].notTakenIndex()]);
			System.out.println("Branches: " + branches[i].type() + " " + branches[i].takenIndex() + " " + branches[i].notTakenIndex());
		}

		assembler.label(exit);
		assembler.jmp(trampoline.relocate(TEST_CASE_FINISH).address());

		var buf = seg.asByteBuffer();
		assembler.assemble(buf::put, seg.address());
		return seg.asSlice(0, buf.position());
	}

	public static void main(String[] args) throws JsonProcessingException {
		var tester = new Tester(0);
		var trampoline = tester.trampoline;
		var scratch1 = tester.scratch1;
		var scratch2 = tester.scratch2;

		var branches = new Branch[]{
				new Branch(ExecutableSequence.BranchType.JNO, 1, 1),
				new Branch(ExecutableSequence.BranchType.JNS, 0, 0),
				new Branch(ExecutableSequence.BranchType.JNE, 4, 1),
				new Branch(ExecutableSequence.BranchType.JNS, 5, 2),
				new Branch(ExecutableSequence.BranchType.JNS, 2, 4)
		};

		var b1 = block(trampoline, branches,
			new byte[][]{new byte[]{(byte) 0x0f, (byte) 0x71, (byte) 0xf3, (byte) 0xd9, },
				new byte[]{(byte) 0x67, (byte) 0xc5, (byte) 0x19, (byte) 0x7d, (byte) 0x24, (byte) 0x25, (byte) 0x10, (byte) 0x0a, (byte) 0x21, (byte) 0x00, },
				new byte[]{(byte) 0x67, (byte) 0x66, (byte) 0x44, (byte) 0x0f, (byte) 0x5b, (byte) 0x14, (byte) 0x25, (byte) 0x20, (byte) 0x05, (byte) 0x11, (byte) 0x00, },
				new byte[]{(byte) 0x48, (byte) 0x09, (byte) 0x34, (byte) 0x25, (byte) 0xb0, (byte) 0x01, (byte) 0x11, (byte) 0x00, },
				new byte[]{(byte) 0x44, (byte) 0x0f, (byte) 0xbd, (byte) 0xce, },
				new byte[]{(byte) 0x67, (byte) 0x44, (byte) 0x8d, (byte) 0x24, (byte) 0x25, (byte) 0x00, (byte) 0x0e, (byte) 0x21, (byte) 0x00, },
				new byte[]{(byte) 0x67, (byte) 0xc5, (byte) 0xf8, (byte) 0x52, (byte) 0x24, (byte) 0x25, (byte) 0x40, (byte) 0x09, (byte) 0x21, (byte) 0x00, },
				new byte[]{(byte) 0x67, (byte) 0xc4, (byte) 0xe2, (byte) 0x59, (byte) 0xbb, (byte) 0x04, (byte) 0x25, (byte) 0x24, (byte) 0x02, (byte) 0x21, (byte) 0x00, },
				new byte[]{(byte) 0x67, (byte) 0x62, (byte) 0x61, (byte) 0x64, (byte) 0x00, (byte) 0x12, (byte) 0x04, (byte) 0x25, (byte) 0x00, (byte) 0x00, (byte) 0x11, (byte) 0x00, },
				new byte[]{(byte) 0x67, (byte) 0x62, (byte) 0x71, (byte) 0xfd, (byte) 0x08, (byte) 0x16, (byte) 0x04, (byte) 0x25, (byte) 0x58, (byte) 0x05, (byte) 0x21, (byte) 0x00, },
			});

		var b2 = block(trampoline, branches,
			new byte[][]{new byte[]{(byte) 0x0f, (byte) 0x71, (byte) 0xf3, (byte) 0xd9, },
				new byte[]{(byte) 0x67, (byte) 0xc5, (byte) 0x19, (byte) 0x7d, (byte) 0x24, (byte) 0x25, (byte) 0x10, (byte) 0x0a, (byte) 0x21, (byte) 0x00, },
				new byte[]{(byte) 0x67, (byte) 0x66, (byte) 0x44, (byte) 0x0f, (byte) 0x5b, (byte) 0x14, (byte) 0x25, (byte) 0x20, (byte) 0x05, (byte) 0x11, (byte) 0x00, },
				new byte[]{(byte) 0x48, (byte) 0x09, (byte) 0x34, (byte) 0x25, (byte) 0xb0, (byte) 0x01, (byte) 0x11, (byte) 0x00, },
				new byte[]{(byte) 0x44, (byte) 0x0f, (byte) 0xbd, (byte) 0xce, },
				new byte[]{(byte) 0x67, (byte) 0x44, (byte) 0x8d, (byte) 0x24, (byte) 0x25, (byte) 0x00, (byte) 0x0e, (byte) 0x21, (byte) 0x00, },
				new byte[]{(byte) 0x67, (byte) 0xc5, (byte) 0xf8, (byte) 0x52, (byte) 0x24, (byte) 0x25, (byte) 0x40, (byte) 0x09, (byte) 0x21, (byte) 0x00, },
				new byte[]{(byte) 0x67, (byte) 0xc4, (byte) 0xe2, (byte) 0x59, (byte) 0xbb, (byte) 0x04, (byte) 0x25, (byte) 0x24, (byte) 0x02, (byte) 0x21, (byte) 0x00, },
				new byte[]{(byte) 0x67, (byte) 0x62, (byte) 0x61, (byte) 0x64, (byte) 0x00, (byte) 0x12, (byte) 0x04, (byte) 0x25, (byte) 0x00, (byte) 0x00, (byte) 0x11, (byte) 0x00, },
				new byte[]{(byte) 0x67, (byte) 0x62, (byte) 0x71, (byte) 0xfd, (byte) 0x08, (byte) 0x16, (byte) 0x04, (byte) 0x25, (byte) 0x58, (byte) 0x05, (byte) 0x21, (byte) 0x00, },
			});

		// print b1 and b2 in hex
		for (var b : b1.toArray(JAVA_BYTE)) {
			System.out.printf("%02x ", b & 0xff);
		}
		System.out.println();

		for (var b : b2.toArray(JAVA_BYTE)) {
			System.out.printf("%02x ", b & 0xff);
		}
		System.out.println();

		var initialState = new ObjectMapper().readValue("""
{"gprs":{"rax":2166016,"rbx":-9111673665810705798,"rcx":1117696,"rdx":-5067229457358941469,"rsi":1072561373488149098,"rdi":1116416,"rbp":3875719106908863061,"r8":-2506804268036839408,"r9":-449241628553831754,"r10":-4338608821600039741,"r11":1114112,"r12":-3021764937220311917,"r13":-4799016846760118104,"r14":1908122402249459326,"r15":1114368,"rsp":2893299774679667909},"zmm":{"zmm":["AAsRAAAAAAAADBEAAAAAAAAHIQAAAAAAYTbBj79JaRsgLLNH01EhZgAHIQAAAAAAAAshAAAAAAAAByEAAAAAAA==","NZbnQqltvUM1vbJ+iOXWHwAIEQAAAAAAByMGhw8DhusAAREAAAAAAOM1E6GoqKn/AAYhAAAAAAAAChEAAAAAAA==","7xdhIm0uVHlujqULmCKd9wAFIQAAAAAAV+x5dfszV9s5TNDUKVZQCQADEQAAAAAAY5x3KAifBXzrX91M8qYpwQ==","Rbt2ezdyonFTC16mFozwvAACEQAAAAAAAAQRAAAAAABL2otDm4VvPQADIQAAAAAAaz4fCYTYWbPvLmHqI+ygDA==","81rf1CjmHaH1b4EbtK0EoQACEQAAAAAAAAIhAAAAAACMBPLmNSWAVOY7XF9fT+ZI9a0YU+fD0IoAABEAAAAAAA==","HqP0NLdEc04AACEAAAAAAE8ACTb9DznDAAkRAAAAAAAACyEAAAAAAAAEEQAAAAAAAAYhAAAAAACKqmM2Ma1sPw==","S4VSKBU3KCZ/BxGV2xE6rgAMIQAAAAAAbfGTcKSXf5UCiQpXXTwWdgALIQAAAAAAAAMhAAAAAAD/nLfSVWt2Bg==","h7At2mYuIEkAASEAAAAAACfm/rXxPV4bAAARAAAAAAA5ol7o41xUywAFEQAAAAAAAAAhAAAAAAApdmqiDG6aTA==","AAARAAAAAAAAAhEAAAAAAIcFSS9nhxe2AAYRAAAAAAAACyEAAAAAAGtcLWsR0JGXZlfM5Z6UiUo1TiBjOH0DTA==","AAQhAAAAAADOL5mLkinjUcTtHUob+11HAAARAAAAAAAACyEAAAAAAAAKIQAAAAAA6Omp0dSdxmkAByEAAAAAAA==","AB8cHQ7s31vO7wriVJhHi3lRSb8G5+HhAAARAAAAAACZm09Go7YdWGa9UNHIF2CJ0vUxXcV1s2gABhEAAAAAAA==","AA4RAAAAAAAADiEAAAAAALyEtiIKPSB6AAshAAAAAACcTUQ7updbEeHX5jQ3yAtYn6so46RO3zEADxEAAAAAAA==","AA8hAAAAAAAADSEAAAAAAAAFEQAAAAAAAAIhAAAAAADUbOWhGStpdwHmTbfz7uGyG/opn3Hc0rIACiEAAAAAAA==","AAAhAAAAAAAACxEAAAAAAAAHEQAAAAAAAAQhAAAAAADdS2G0KfXkOwAEIQAAAAAAAAshAAAAAAAABxEAAAAAAA==","Qte8vvzpZbhJIHnhK3LYJAAEEQAAAAAAAAshAAAAAAAACBEAAAAAAAAHIQAAAAAAyWCIGLEWSn8CAOC9Sp8e5Q==","AAcRAAAAAABlPdOYzGT2CAAOEQAAAAAAAA4RAAAAAAAADhEAAAAAAAAAIQAAAAAA/04pg5gEPu7AijHB9CGG5w==","tevG1KE/3JcACCEAAAAAANiCU9h89fRxBVEZ5TgGGie196VGBl5pTAALIQAAAAAAVUOiv3cY7GAADBEAAAAAAA==","DAnWUP+pGGHYpxCMXw6tWFl4w0eef7wkDVqO8CkDEPCg8WWjaVtSsq7XzbG9s4K2AAMhAAAAAADKK1xqUiwMsw==","Eo6qSLZZWUlsBRxdRLj5uQADEQAAAAAAfJRtmqoWVKmXbNgIF01r2gAGIQAAAAAAAA4RAAAAAAAABBEAAAAAAA==","cHerlU2SP/8ACxEAAAAAAAALEQAAAAAA7qDNVnEeuL8ACSEAAAAAAAAHEQAAAAAAAAsRAAAAAADwmcQfYubMOw==","ddB1fANasXEoLE2ccsziPgANEQAAAAAAVpEqnDU3M9bnSNFz6XmsdDM99MPGNBaGaPvdjvL7MRAeECu06ofQ2A==","JKGU+0pmvvWcID48CJ5VNAANEQAAAAAALJH/y105wDXLvSQP08usHAipiAKqUzzQAA8hAAAAAADuixvFlsbQ4A==","i2NQjyDag2ydAWs72unz3wABEQAAAAAAAA0hAAAAAABJSmWSnI5PtAAOIQAAAAAAAAURAAAAAAAADREAAAAAAA==","1ntu3BjzBZ87Co4HJepztAANEQAAAAAAAA8hAAAAAAAABBEAAAAAAAANIQAAAAAA8DBBxqPteGIAAREAAAAAAA==","AAghAAAAAAAABxEAAAAAAOhKNqdEZzF9twdMYHJVNi0UJnencvfXd0jB1bFQRDqGa0htjXqwt+8AABEAAAAAAA==","yqxeJEE3lr8ACiEAAAAAAOdHvJqZvnkBAC4peDqY07KSppOfDaMheAABEQAAAAAAAA8hAAAAAAAADCEAAAAAAA==","4V0XdAeoLh8AASEAAAAAAJytdXlkENAmreUZfHDvQc1A5LDQjMH1k5B1Q8Ib0dP0AAERAAAAAABvSkPeDTBZRA==","GkJVI4kfAKD1go6L4sI+mAAFIQAAAAAAAAURAAAAAAAADSEAAAAAALMkkZTeTaFVAA4hAAAAAAAAAhEAAAAAAA==","AAchAAAAAAAADBEAAAAAAAAAIQAAAAAAZl+QZwbu8X4c128VTlREWAAEEQAAAAAAAAohAAAAAAAADREAAAAAAA==","AA4RAAAAAAAABhEAAAAAAAM8tSeM8nZa/vR91uRymngAAxEAAAAAAMFlBzMXmG79AAwhAAAAAABuAOvgMzVPaw==","AA4RAAAAAACgfIxOpBe+lCr+oeXbWPKwAA8hAAAAAAAAAyEAAAAAAJkwv9cas6btAAURAAAAAADkcEO+UqSoDQ==","AAARAAAAAABYmiETArAMIYoV8JjkbWMQAA8RAAAAAAAADREAAAAAAAAFEQAAAAAAZxqsHR7huWpTJ5DNbOreBA=="]},"mmx":{"mm":[17311452958284151,2828519812291503034,1116416,-2984318363922273096,1114112,2163456,2163712,2554373399381726314]},"rflags":8278494522506432638}
""", CPUState.class);

		initialState = CPUState.filledWith(0);

		for (int i = 0; i < 16; i += 8) {
			ByteBuffer.wrap(initialState.zmm().zmm()[19]).putLong(i, 0xdeadbeefdeadbeefL);
		}

		for (int i = 0; i < 1; i++) {
			scratch1.fill((byte) 0);
			scratch2.fill((byte) 0);
			var result1 = tester.runBlock(initialState, b1);
			if (result1 instanceof ExecutionResult.Success(CPUState (_, CPUState.VectorRegisters (byte[][] zmm), _, _))) {
				boolean allZeros = true;
				for (int i1 = 0; i1 < zmm[24].length; i1++) {
					allZeros &= zmm[24][i1] == 0;
				}

				if (allZeros) {
					System.out.println("GOOD");
					return;
				}
			}
		}

		System.out.println("BAD");
	}
}
