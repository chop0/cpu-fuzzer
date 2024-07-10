.set MAX_THREADS, 96
.set SIGSTKSZ, 8192
.set NUM_IGNORED_SIGNALS, 5

# typedef  struct {
#             void *fs_base;
#             void *gs_base;
#             void *trampoline_address;
#             void *sigstk_address;
#             void *jstack_address;
#             uint32_t pkru_restore;
#
#             bool taken;
#             bool active;
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

# Before doing anything, we should clear $pkru and save the current $rsp.  We'll restore it to whatever it's supposed to be later.
    mov %rsp, %r10
    xchg %rdx, %r8 # save this

    xor %ecx, %ecx
    xor %edx, %edx
    rdpkru              # we'll restore this, if we enter the Java signal handler
    mov %rax, %r9

    xor %eax, %eax # clear for now
    wrpkru

    xchg %r8, %rdx
    mov %r10, %r15
    push %rdi
    push %rsi
    push %rdx # save the handler arguments for later...
# now, we should be on the signal handler stack, and it should be writeable.  find the signal stack slot by taking $rsp, subtracting signal_stack_region and dividing by SIGSTKSZ
# (right shift by 13)
    mov signal_stack_region@GOTPCREL(%rip), %r11
    sub 0(%r11), %r10
    shr $13, %r10

    cmpq $MAX_THREADS, %r10
    jae fucked_stack_address

    # compute offset in active_thread_info
    imul $THREAD_INFO_SIZE, %r10, %r10
    mov active_thread_info@GOTPCREL(%rip), %r11
    add %r11, %r10

    cmpb $0, ACTIVE_OFFSET(%r10)
    je java_signal_handler

    mov FS_BASE_OFFSET(%r10), %r11
    mov GS_BASE_OFFSET(%r10), %r12
    wrfsbase %r11
    wrgsbase %r12

    mov test_case_exit@GOTPCREL(%rip), %r11
    mov routine_begin@GOTPCREL(%rip), %r12
    sub %r12, %r11
    add TRAMPOLINE_ADDRESS_OFFSET(%r10), %r11
    mov $1, FAULT_OFFSET(%r10)

    mov 0(%rsi), %r13d
    mov %r13d, FAULT_REASON_OFFSET(%r10)

    mov 8(%rsi), %r13d
    mov %r13d, FAULT_CODE_OFFSET(%r10)

    mov 16(%rsi), %r13
    mov %r13, FAULT_ADDRESS_OFFSET(%r10)

    jmp %r11

java_signal_handler:
    pop %rdx
    pop %rsi
    pop %rdi
    mov JSTACK_ADDRESS_OFFSET(%r10), %rsp

    mov ignored_signals@GOTPCREL(%rip), %rbx
    xor %rcx, %rcx
# scan through ignored_signals to find where %rdi matches the value
.loop:
    cmp 0(%rbx, %rcx, 8), %edi
    je .end
    inc %rcx
    cmpq $NUM_IGNORED_SIGNALS, %rcx
    jb .loop
    jmp invalid_signal
.end:
    mov java_signal_handlers@GOTPCREL(%rip), %r12
    call 0(%r12,%rcx,8)
    mov %r15, %rsp
    ret

fucked_stack_address:
    mov %rdi, 2
    call exit@plt
invalid_signal:
    mov %rdi, 1
    call exit@plt