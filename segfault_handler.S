.set MAX_THREADS, 96
.set SIGSTKSZ, 8192
.set NUM_IGNORED_SIGNALS, 5

# typedef  struct {
#			 void *fs_base;
#			 void *gs_base;
#			 void *trampoline_address;
#			 void *sigstk_address;
#			 void *jstack_address;
#			 uint32_t pkru_restore;
#
#			 bool taken;
#			 bool active;
#			 bool faulted;
#			 struct fault_details fault_details;
# } thread_info_t;
.set FS_BASE_OFFSET, 0
.set GS_BASE_OFFSET, 8
.set TRAMPOLINE_ADDRESS_OFFSET, 16
.set SIGSTK_ADDRESS_OFFSET, 24
.set JSTACK_ADDRESS_OFFSET, 32
.set PKRU_RESTORE_OFFSET, 40
.set TAKEN_OFFSET, 44
.set ACTIVE_OFFSET, 45
.set FAULT_OFFSET, 46
.set FAULT_ADDRESS_OFFSET, 48
.set FAULT_REASON_OFFSET, 56
.set FAULT_CODE_OFFSET, 60
.set THREAD_INFO_SIZE, 64

.globl signal_handler

signal_handler:
# When we enter the signal handler, there are two possibilities:
# a.  We're currently running a test case, and should jump to the test case's trampoline to perform a context switch
# b.  We're not currently running a test case, and should jump to Java's signal handler
	pushq %rbp
	movq %rsp, %rbp

	subq $48, %rsp

	movq %rdi, -8(%rbp)
	movq %rsi, -16(%rbp)
	movq %rdx, -24(%rbp)
	
# now, we should be on the signal handler stack, and it should be writeable.  find the signal stack slot by taking $rsp, subtracting signal_stack_region and dividing by 2*SIGSTKSZ
# (right shift by 14)
	mov signal_stack_region@GOTPCREL(%rip), %r11
	movq %rsp, %r10
	sub 0(%r11), %r10
	shr $14, %r10
	mov %r10, %rdi

	cmpq $MAX_THREADS, %r10
	jae weird_stack_address

valid_stack_address:
	imul $THREAD_INFO_SIZE, %r10, %r10
	mov active_thread_info@GOTPCREL(%rip), %r11
	addq %r11, %r10
	movq %r10, -32(%rbp)
	jmp check_active

weird_stack_address: # our stack address isn't in the region we allocated for it.  first, try seeing if the stack hasn't been setup for this thread yet
	call maybe_allocate_signal_stack
	test %rax, %rax
	jz fucked_stack_address # maybe_alloate_signal_stack returned NULL, indicating that the stack already has been allocated for this thread -- something has gone terribly wrong
	mov %rax, -32(%rbp)
	jmp check_active

check_active:
	movq -32(%rbp), %r10

	cmpb $0, ACTIVE_OFFSET(%r10)
	je java_signal_handler

	mov FS_BASE_OFFSET(%r10), %r8
	wrfsbase %r8
	mov GS_BASE_OFFSET(%r10), %r8
	wrgsbase %r8

	call check_index
	movq -32(%rbp), %r10

	movb $1, FAULT_OFFSET(%r10)

	movl 0(%rsi), %r8d
	movl %r8d, FAULT_REASON_OFFSET(%r10)

	movl 8(%rsi), %r8d
	movl %r8d, FAULT_CODE_OFFSET(%r10)

	movq 16(%rsi), %r8
	movq %r8, FAULT_ADDRESS_OFFSET(%r10)

	mov test_case_exit@GOTPCREL(%rip), %r9
	sub routine_begin@GOTPCREL(%rip), %r9
	add TRAMPOLINE_ADDRESS_OFFSET(%r10), %r9

	jmp *%r9

java_signal_handler:
	movq -8(%rbp), %rdi

	mov ignored_signals@GOTPCREL(%rip), %r11
	xor %rcx, %rcx
# scan through ignored_signals to find where %rdi matches the value
.loop:
	cmp 0(%r11, %rcx, 4), %edi
	je .end
	inc %rcx
	cmpq $NUM_IGNORED_SIGNALS, %rcx
	jb .loop
	jmp invalid_signal
.end:
	# load args to java sig handler
	movq -8(%rbp), %rdi
	movq -16(%rbp), %rsi
	movq -24(%rbp), %rdx

	mov java_signal_handlers@GOTPCREL(%rip), %r11
	call *0(%r11,%rcx,8)

	addq $48, %rsp
	popq %rbp
	ret

fucked_stack_address:
	leaq fucked_stack_address_msg(%rip), %rdi
	call puts
	call abort@plt
invalid_signal:
	leaq invalid_signal_msg(%rip), %rdi
	call abort@plt

fucked_stack_address_msg: .asciz "Stack address fucked"
invalid_signal_msg: .asciz "Invalid signal"