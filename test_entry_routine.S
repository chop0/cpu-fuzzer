.global routine_begin
.global test_case_entry
.global test_case_exit
.global routine_end

.macro push_unclobberable_gprs
    push %r12
    push %r13
    push %r14
    push %r15
    push %rbx
    push %rbp
    pushfq
.endm

.macro pop_unclobberable_gprs
    popfq
    pop %rbp
    pop %rbx
    pop %r15
    pop %r14
    pop %r13
    pop %r12
.endm

.macro store_mxcsr op
    sub $0x8, %rsp
    movq $0, (%rsp)
    stmxcsr (%rsp)
    popq %\op
.endm

.macro load_mxcsr op
    pushq %\op
    ldmxcsr (%rsp)
    add $0x8, %rsp
.endm

.macro mask_mxcsr_and_push_old
    store_mxcsr r12
    push %r12
    or $0x1F80, %r12
    load_mxcsr r12
.endm

.macro pop_old_mxcsr
    ldmxcsr (%rsp)
    add $0x8, %rsp
.endm

.macro mask_fenv_and_push_old
    sub $28, %rsp
    fnstenv (%rsp)
.endm

.macro pop_old_fenv
    fldenv (%rsp)
    add $28, %rsp
.endm

.macro save_fs_gs_base
    rdgsbase %r12
    movq %r12, saved_gs(%rip)
    rdfsbase %r12
    movq %r12, saved_fs(%rip)
.endm

.macro restore_fs_gs_base
    movq saved_fs(%rip), %r12
    wrfsbase %r12
    movq saved_gs(%rip), %r12
    wrgsbase %r12
.endm

.macro save_unclobberable_state
    push_unclobberable_gprs
    mask_mxcsr_and_push_old
    mask_fenv_and_push_old
    save_fs_gs_base
    movq %rsp, saved_rsp(%rip)
.endm

.macro restore_unclobberable_state
    movq saved_rsp(%rip), %rsp
    restore_fs_gs_base
    pop_old_fenv
    pop_old_mxcsr
    pop_unclobberable_gprs
.endm

.altmacro

.macro write_vector_regs_ bank, start, end, size, offset=0
    vmovups %\bank\start, offset (%r15)
    .if \end-\start
        write_vector_regs_ bank, %(start+1), \end, \size, (\offset+\size)
    .endif
.endm

.macro read_vector_regs_ bank, start, end, size, offset=0
    vmovups offset (%r15), %\bank\start
    .if \end-\start
        read_vector_regs_ bank, %(\start+1), \end, \size, %(\offset+\size)
    .endif
.endm

.macro vector_regs_ func, offset=0
    LOCAL avx512
    LOCAL avx
    LOCAL sse
    LOCAL done

    mov $0x7, %eax
    xor %ecx, %ecx
    cpuid
    btl $16, %ebx
    jc avx512

    mov $0x1, %eax
    xor %ecx, %ecx
    cpuid
    btl $28, %ecx
    jc avx

    btl $25, %edx
    jc sse

    jmp done

avx512:
    func zmm, 0, 31, 64, offset
    jmp done

avx:
    func ymm, 0, 15, 64, offset
    jmp done

sse:
    func xmm, 0, 7, 64, offset
    jmp done

done:
.endm

.macro read_vector_regs offset=0
    vector_regs_ read_vector_regs_, offset
.endm

.macro write_vector_regs offset=0
    vector_regs_ write_vector_regs_, offset
.endm

.text
routine_begin:
saved_rsp:
    .quad 0
addr:
    .quad 0
saved_fs:
    .quad 0
saved_gs:
    .quad 0
saved_state:
    .quad 0
temp:
    .quad 0


test_case_entry:
    movq %rdi, addr(%rip)
    movq %rsi, saved_state(%rip)

    save_unclobberable_state

    movq saved_state(%rip), %r15
    read_vector_regs 144
    movq 2192(%r15), %mm0
    movq 2200(%r15), %mm1
    movq 2208(%r15), %mm2
    movq 2216(%r15), %mm3
    movq 2224(%r15), %mm4
    movq 2232(%r15), %mm5
    movq 2240(%r15), %mm6
    movq 2248(%r15), %mm7

    movq 0(%r15), %rax
    wrfsbase %rax
    movq 8(%r15), %rax
    wrgsbase %rax
    movq 16(%r15), %rax
    movq 24(%r15), %rbx
    movq 32(%r15), %rcx
    movq 40(%r15), %rdx
    movq 48(%r15), %rsi
    movq 56(%r15), %rdi
    movq 64(%r15), %rbp
    movq 72(%r15), %r8
    movq 80(%r15), %r9
    movq 88(%r15), %r10
    movq 96(%r15), %r11
    movq 104(%r15), %r12
    movq 112(%r15), %r13
    movq 120(%r15), %r14
    lea 2256(%r15), %rsp
    popfq
    movq 136(%r15), %rsp
    movq 128(%r15), %r15

    jmp *addr(%rip)
test_case_exit:

    movq %r15, temp(%rip)
    movq saved_state(%rip), %r15
    movq %rax, 16(%r15)
    rdfsbase %rax
    movq %rax, 0(%r15)
    rdgsbase %rax
    movq %rax, 8(%r15)

    movq %rbx, 24(%r15)
    movq %rcx, 32(%r15)
    movq %rdx, 40(%r15)
    movq %rsi, 48(%r15)
    movq %rdi, 56(%r15)
    movq %rbp, 64(%r15)
    movq %r8, 72(%r15)
    movq %r9, 80(%r15)
    movq %r10, 88(%r15)
    movq %r11, 96(%r15)
    movq %r12, 104(%r15)
    movq %r13, 112(%r15)
    movq %r14, 120(%r15)
    movq temp(%rip), %rax
    movq %rax, 128(%r15)
    movq %rsp, 136(%r15)

    lea 2264(%r15), %rsp
    pushfq

    write_vector_regs 144
    movq %mm0, 2192(%r15)
    movq %mm1, 2200(%r15)
    movq %mm2, 2208(%r15)
    movq %mm3, 2216(%r15)
    movq %mm4, 2224(%r15)
    movq %mm5, 2232(%r15)
    movq %mm6, 2240(%r15)
    movq %mm7, 2248(%r15)

    restore_unclobberable_state
    ret
routine_end: