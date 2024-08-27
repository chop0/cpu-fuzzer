.intel_syntax noprefix
.global trampoline_begin
.global trampoline_entrypoint
.global trampoline_exitpoint
.global trampoline_end

.macro push_unclobberable_gprs
    push r12
    push r13
    push r14
    push r15
    push rbx
    push rbp
    pushfq
.endm

.macro pop_unclobberable_gprs
    popfq
    pop rbp
    pop rbx
    pop r15
    pop r14
    pop r13
    pop r12
.endm

.macro store_mxcsr op
    sub rsp, 8
    mov qword ptr [rsp], 0
    stmxcsr [rsp]
    popq \op
.endm

.macro load_mxcsr op
    pushq \op
    ldmxcsr [rsp]
    add rsp, 8
.endm

.macro mask_mxcsr_and_push_old
    store_mxcsr r12
    push r12
    or r12, 0x1F80
    load_mxcsr r12
.endm

.macro pop_old_mxcsr
    ldmxcsr [rsp]
    add rsp, 8
.endm

.macro mask_fenv_and_push_old
    sub rsp, 28
    fnstenv [rsp]
.endm

.macro pop_old_fenv
    fldenv [rsp]
    add rsp, 28
.endm

.macro save_fs_gs_base
    rdgsbase r12
    mov qword ptr [rip+saved_gs], r12
    rdfsbase r12
    mov qword ptr [rip+saved_fs], r12
.endm

.macro restore_fs_gs_base
    mov r12, qword ptr [rip+saved_fs]
    wrfsbase r12
    mov r12, qword ptr [rip+saved_gs]
    wrgsbase r12
.endm

.macro save_unclobberable_state
    push_unclobberable_gprs
    mask_mxcsr_and_push_old
    mask_fenv_and_push_old
    save_fs_gs_base
    mov qword ptr [rip+saved_rsp], rsp
.endm

.macro restore_unclobberable_state
    mov rsp, qword ptr [rip+saved_rsp]
    restore_fs_gs_base
    pop_old_fenv
    pop_old_mxcsr
    pop_unclobberable_gprs
.endm

.altmacro

.macro write_vector_regs_ bank, start, end, size, offset=0
    vmovups \bank\start, [r15+offset]
    .if \end-\start
        write_vector_regs_ bank, %(\start+1), \end, \size, %(\offset+\size)
    .endif
.endm

.macro read_vector_regs_ bank, start, end, size, offset=0
    vmovups [r15+offset], \bank\start
    .if \end-\start
        read_vector_regs_ bank, %(\start+1), \end, \size, %(\offset+\size)
    .endif
.endm

.macro vector_regs_ func, offset=0
    LOCAL avx512
    LOCAL avx
    LOCAL sse
    LOCAL done

    mov eax, 0x7
    xor ecx, ecx
    cpuid
    bt ebx, 16
    jc avx512

    mov eax, 0x1
    xor ecx, ecx
    cpuid
    bt ecx, 28
    jc avx

    bt edx, 25
    jc sse

    jmp done

avx512:
    func zmm, 0, 31, 64, offset
    kxorq k0, k0, k0
    kxorq k1, k1, k1
    kxorq k2, k2, k2
    kxorq k3, k3, k3
    kxorq k4, k4, k4
    kxorq k5, k5, k5
    kxorq k6, k6, k6
    kxorq k7, k7, k7
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
trampoline_begin:
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

trampoline_entrypoint:
    mov rdi, qword ptr [rip+addr]
    mov rsi, qword ptr [rip+saved_state]

    save_unclobberable_state

    mov r15, qword ptr [rip+saved_state]
    read_vector_regs 144
    movq mm0, qword ptr [r15+2192]
    movq mm1, qword ptr [r15+2200]
    movq mm2, qword ptr [r15+2208]
    movq mm3, qword ptr [r15+2216]
    movq mm4, qword ptr [r15+2224]
    movq mm5, qword ptr [r15+2232]
    movq mm6, qword ptr [r15+2240]
    movq mm7, qword ptr [r15+2248]

    mov rax, qword ptr [r15]
    wrfsbase rax
    mov rax, qword ptr [r15+8]
    wrgsbase rax
    mov rax, qword ptr [r15+16]
    mov rbx, qword ptr [r15+24]
    mov rcx, qword ptr [r15+32]
    mov rdx, qword ptr [r15+40]
    mov rsi, qword ptr [r15+48]
    mov rdi, qword ptr [r15+56]
    mov rbp, qword ptr [r15+64]
    mov r8, qword ptr [r15+72]
    mov r9, qword ptr [r15+80]
    mov r10, qword ptr [r15+88]
    mov r11, qword ptr [r15+96]
    mov r12, qword ptr [r15+104]
    mov r13, qword ptr [r15+112]
    mov r14, qword ptr [r15+120]
    lea rsp, [r15+2256]
    popfq
    mov rsp, qword ptr [r15+136]
    mov r15, qword ptr [r15+128]


    jmp qword ptr [rip+addr]
trampoline_exitpoint:

    mov r15, qword ptr [rip+temp]
    mov r15, qword ptr [rip+saved_state]
    mov rax, 16[r15]
    rdfsbase rax
    mov qword ptr [r15], rax
    rdgsbase rax
    mov qword ptr [r15+8], rax

    mov rbx, qword ptr [r15+24]
    mov rcx, qword ptr [r15+32]
    mov rdx, qword ptr [r15+40]
    mov rsi, qword ptr [r15+48]
    mov rdi, qword ptr [r15+56]
    mov rbp, qword ptr [r15+64]
    mov r8, qword ptr [r15+72]
    mov r9, qword ptr [r15+80]
    mov r10, qword ptr [r15+88]
    mov r11, qword ptr [r15+96]
    mov r12, qword ptr [r15+104]
    mov r13, qword ptr [r15+112]
    mov r14, qword ptr [r15+120]
    mov rax, qword ptr [rip+temp]
    mov qword ptr [r15+128], rax
    mov rsp, qword ptr [r15+136]

    lea rsp, [r15+2264]
    pushfq

    write_vector_regs 144
    movq qword ptr [r15+2192], mm0
    movq qword ptr [r15+2200], mm1
    movq qword ptr [r15+2208], mm2
    movq qword ptr [r15+2216], mm3
    movq qword ptr [r15+2224], mm4
    movq qword ptr [r15+2232], mm5
    movq qword ptr [r15+2240], mm6
    movq qword ptr [r15+2248], mm7

    restore_unclobberable_state
    ret
trampoline_end:
