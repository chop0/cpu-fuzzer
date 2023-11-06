grammar Operand;

operand          : implicitNumber | mem | moffs | saeControl | imm
                 | tileStride | reg | mask | vsib
                 | vectorOperand | fixedReg | embeddedRC
                 ;

implicitNumber   : DIGITS;
embeddedRC       : 'ER';

mem              : 'MEM' | MEMORY_SIZE FP_ADDENDUM? | 'M';
moffs            : 'MOFFS' DIGITS;
saeControl       : 'SAE';
imm              : 'IMM' DIGITS;
tileStride       : 'SIBMEM';

reg              : 'R' DIGITS? MEMORY_SIZE?
                 ;

fixedReg         : ('AL' | 'CL' | 'DL' | 'BL' | 'AH' | 'CH' | 'DH' | 'BH' | 'SIL' | 'DIL')
                 | ('AX' | 'CX' | 'DX' | 'BX' | 'SI' | 'DI')
                 | ('RAX' | 'RCX' | 'RDX' | 'RBX' | 'RSI' | 'RDI')
                 | ('EAX' | 'ECX' | 'EDX' | 'EBX' | 'ESI' | 'EDI')
                 | 'GS' | 'FS' | 'ES' | 'DS' | 'SS' | 'CS'
                 | 'ST' DIGITS
                 ;

mask             : 'K1' Z?;

vsib             : 'V' MEMORY_SIZE vector_bank_short;
vectorOperand    : VECTOR_BANK MULTIREG_COUNT? MEMORY_SIZE? BROADCAST_SIZE?;

vector_bank_short: X | Y | Z;
VECTOR_BANK      : 'MM' | 'XMM' | 'YMM' | 'ZMM' | 'TMM' | 'STI' | 'SREG' | 'KR' | 'K';

MEMORY_SIZE      : M DIGITS;
BROADCAST_SIZE   : B DIGITS;
MULTIREG_COUNT   : P DIGITS;
DIGITS           : DIGIT+;

M: 'M';
B: 'B';
P: 'P';
X: 'X';
Y: 'Y';
Z                : 'Z';
FP_ADDENDUM      : 'FP' | 'INT' | 'FLOAT' | 'BYTE' | 'BCD';

fragment DIGIT   : [0-9];
