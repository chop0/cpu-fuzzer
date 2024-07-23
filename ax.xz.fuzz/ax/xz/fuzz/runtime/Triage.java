package ax.xz.fuzz.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.icedland.iced.x86.asm.CodeAssembler;
import com.github.icedland.iced.x86.asm.CodeLabel;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static ax.xz.fuzz.runtime.ExecutableSequence.TEST_CASE_FINISH;
import static ax.xz.fuzz.runtime.ExecutionResult.interestingMismatch;
import static ax.xz.fuzz.tester.slave_h.*;
import static com.github.icedland.iced.x86.asm.AsmRegisters.r15;
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
			new Branch(ExecutableSequence.BranchType.JO, 1, 1)
		};

		var b1 = block(trampoline, branches,
			new byte[][]{new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0xfb, (byte) 0xcd, },
				new byte[]{(byte) 0x67, (byte) 0x66, (byte) 0x0f, (byte) 0x57, (byte) 0x0c, (byte) 0x25, (byte) 0x20, (byte) 0x04, (byte) 0x21, (byte) 0x00, },
				new byte[]{(byte) 0x66, (byte) 0x67, (byte) 0x0f, (byte) 0xbb, (byte) 0x14, (byte) 0x25, (byte) 0x00, (byte) 0x0b, (byte) 0x21, (byte) 0x00, },
			});

		var b2 = block(trampoline, branches,
			new byte[][]{new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0xfb, (byte) 0xcd, },
				new byte[]{(byte) 0x66, (byte) 0x67, (byte) 0x0f, (byte) 0xbb, (byte) 0x14, (byte) 0x25, (byte) 0x00, (byte) 0x0b, (byte) 0x21, (byte) 0x00, },
				new byte[]{(byte) 0x67, (byte) 0x66, (byte) 0x0f, (byte) 0x57, (byte) 0x0c, (byte) 0x25, (byte) 0x20, (byte) 0x04, (byte) 0x21, (byte) 0x00, },
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
{"gprs":{"rax":7499826824358182689,"rbx":1116160,"rcx":2162688,"rdx":6855485519580547351,"rsi":-571229346786760823,"rdi":-4344841374730244575,"rbp":2165248,"r8":6229710751356351235,"r9":8984263798544311836,"r10":-6582228353864420016,"r11":1114368,"r12":1117696,"r13":2163712,"r14":2163200,"r15":2163712,"rsp":2166016},"zmm":{"zmm":["AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==","AAIRAAAAAAApgK2euVy5+mQld+4ueR7bCEhibZKw37jNeBs39C5XvAAIEQAAAAAAAAERAAAAAABCyEJhIRqvwQ==","fjuRBh3rs0lsMSpMsI2uvgAJEQAAAAAAAAohAAAAAAAADREAAAAAAAAOIQAAAAAAm4bMaqJa4w/CBop+5W84/g==","AAcRAAAAAAAABSEAAAAAADoHtYA7tdzVAA8RAAAAAABiyP+jXPI5P6QkAhyJbIGTAAoRAAAAAAAJbMRk1fUSSw==","58EvjmV4FYuQYnyqs73YDvFhsLtgqzLFId+JArPiVyAACBEAAAAAAAALEQAAAAAAkBda0IZ8+2DRmVqwX1z8Iw==","AAghAAAAAAAAASEAAAAAAAAJEQAAAAAAXeuy0Y4uFjErbYI4vx3WvwAIEQAAAAAAAAYRAAAAAAAACREAAAAAAA==","K8Dm4jDaUZqzXMtRw2JHeQAAIQAAAAAAAAAhAAAAAAAACREAAAAAANGav0mgwJs6AAohAAAAAAC2rR/DdF2cHw==","V4u5/IoJdo4ADREAAAAAAG1wPAQEOP4YGjGDwupeeXFiFvLCKl14yni0fpi6elnjAAYRAAAAAABKH/uw5fuSQw==","+2ZmODISpQIACxEAAAAAAJTGQv/MfvRRKjIch89GiM8AACEAAAAAAAAOIQAAAAAAbm/WNjpE573YhXw9vUO8LA==","AAwRAAAAAAAPRKwwmz4qJY5nkuff7+QwoSOAXvToBIQABBEAAAAAAAALIQAAAAAAuUdEz/TF9ZIE6zDES2zt2w==","AAkhAAAAAAAADREAAAAAAAAIIQAAAAAA8kggFMSpO9EACxEAAAAAAAAIIQAAAAAAAAURAAAAAAAAASEAAAAAAA==","7Mrc/xU5H+pleJaHkWgX0AALEQAAAAAAAAAhAAAAAACOImk5vzwV37UQ8U2FsLrMAA4RAAAAAAAACBEAAAAAAA==","AA0RAAAAAAAACSEAAAAAAIOgOvz+EH11AA4RAAAAAAAYgiOiMnUtlMV46n/o+cn4AAQRAAAAAACU38V9HTSUQQ==","AAkRAAAAAAAAChEAAAAAAPereEVh7ob9AAwhAAAAAAAAAREAAAAAALuhAjWbEpvSAAoRAAAAAAAFcgFAFMMxuA==","CRmRzwm0HxMACCEAAAAAAGYLkiuo9jXSAAAhAAAAAADK/gKu9/7htAAEIQAAAAAAgoPhFVqLpur4HSxA9RMWGQ==","mhtayst/MolM+luuolPVIgANIQAAAAAAahLGKK8cPYKiDLyKRzMIYQAKIQAAAAAACDa82STsj3NWgFC0B+K0GA==","+t542lAPISjqeHAjz/112gAHEQAAAAAAJfQz728o8UAADyEAAAAAAHKLJq8PVpUiAA0hAAAAAAC3jHkF1WPvUA==","AAsRAAAAAAAABhEAAAAAAAANIQAAAAAAAAkRAAAAAAClmICxn+xqmwAEIQAAAAAAhFHztjikdn8ACSEAAAAAAA==","u5PdE9FQFxkABREAAAAAAAADIQAAAAAAWT5joophvr8ACiEAAAAAAIgGfxQG/ysVAAURAAAAAABuCq7uvE6/UA==","JW8MMYS0XkcyLwcXZWYWkioRy1WlD4kFoP+VhJwIOqEABSEAAAAAAIupHoLfBDpzAAoRAAAAAAAAAxEAAAAAAA==","zuYomwCke8kADyEAAAAAAPdDhyom0Bl1AAYhAAAAAADoDP103CM0zwANIQAAAAAAAAURAAAAAAAADyEAAAAAAA==","sCd9BAnvNMQdUIv7SxuRlgAEIQAAAAAAMvr7Btj+WgWd3mGTtUYd7QABIQAAAAAADuGtSshxecv+APpNlSi0vg==","qSl+H8gG7TA6SWT+tX7X25+6xfag/FeKexLg5dQcgLT98gj2WjY2AmrrZ5p3fKtcTIFy9i3dnC9Ua5lEWJ999w==","AA0RAAAAAACuOwW+4j2Sv88ANsHXzGygeyJ6GSsDhDoACSEAAAAAAAADEQAAAAAAgMEDCjUGKTNLFCyrLHGf6Q==","AAgRAAAAAAAADxEAAAAAAAAJEQAAAAAAmSzBMbCjieIAAyEAAAAAAAAHIQAAAAAAAAUhAAAAAADJf6vmwDzCnw==","AAMRAAAAAACl3FpxuDZLZgAFIQAAAAAAAAUhAAAAAAAAByEAAAAAAH5iMpOmhsXLAAIRAAAAAAAAAREAAAAAAA==","Ez0DlcC4fLwACREAAAAAAFN6R55PIRxrqu94FW97xMoAAiEAAAAAAKl8wrvMqACjAAwRAAAAAAAABxEAAAAAAA==","AAEhAAAAAAAAASEAAAAAAAAOIQAAAAAAAAgRAAAAAAAAASEAAAAAAGxFAaDqj1vdAA8RAAAAAABUI0R7SGPiBQ==","AAURAAAAAAAACSEAAAAAAAABEQAAAAAAAA4hAAAAAAAABiEAAAAAAAAEEQAAAAAAsqNpCmkQpWQADhEAAAAAAA==","AAcRAAAAAAAABSEAAAAAAAAFEQAAAAAAcr+5Jehsc8aO1JhkQJbXL0byJK7PRRPaAAgRAAAAAAAAChEAAAAAAA==","tTq00wVMuPPwKvAZY/l8IJqkPgr4UmjmAAgRAAAAAAAACBEAAAAAAKXIIJXNMdr5R0sA/GHfVNAAAyEAAAAAAA=="]},"mmx":{"mm":[-474407544239481056,5158151520311458522,1288719360744843475,1117696,300844207695738962,1115392,1115648,7107841410744608149]},"rflags":5618914537267122285}
""", CPUState.class);

		initialState = new CPUState(
			initialState.gprs().withZeroed(0),
			initialState.zmm().withZeroed(0, 32),
			CPUState.MMXRegisters.filledWith(0L),
			0
		);
//		System.out.println(initialState.zmm());

//		for (int i = 0; i < 16; i += 8) {
//			ByteBuffer.wrap(initialState.zmm().zmm()[19]).putLong(i, 0xdeadbeefdeadbeefL);
//		}

		scratch1.fill((byte) 0);
		scratch2.fill((byte) 0);
		var result1 = tester.runBlock(initialState, b1);
		scratch1.fill((byte) 0);
		scratch2.fill((byte) 0);
		var result2 = tester.runBlock(initialState, b2);

		if (interestingMismatch(result1, result2)) {
			System.out.println(result1);
			System.out.println(result2);
		}
	}
}
