grammar InstructionDefs;


instructionDefs
    : instructionDef* EOF
    ;

instructionDef: INSTRUCTION_BEGIN op PIPE instr PIPE cpuid (PIPE tupleType)?;

op: specialOp | legacyOp | d3NowOp | vexOp | xopOp | evexOp | mvexOp;

specialOp: '<' specialOpName '>';
specialOpName: 'INVALID' | 'db' | 'dw' | 'dd' | 'dq';

legacyOp: legacyOpDescriptor* legacyOpName;
legacyOpDescriptor: OCTET | 'o16' | 'o32' | 'o64' | 'REX.W' | 'a16' | 'a32' | 'a64';
legacyOpName: opCodeByte opCodeByte? opCodeByte?;

d3NowOp: D3_NOW_PREFIX opCodeByte;
vexOp: VEX_PREFIX (DOT vecEncPart)* opCodeByte;
xopOp: XOP_PREFIX (DOT vecEncPart)* opCodeByte;
evexOp: EVEX_PREFIX (DOT vecEncPart)* opCodeByte;
mvexOp: MVEX_PREFIX (DOT vecEncPart)* opCodeByte;

vecEncPart: '0F' | '0F38' | '0F3A' | 'MAP5' | 'MAP6' | 'X8' | 'X9' | 'XA' | 'NP' | OCTET | 'L0' | 'L1' | 'LIG' | 'LZ' | '128' | '256' | '512' | 'W0' | 'W1' | 'WIG' | 'NDS' | 'NDD' | 'EH0' | 'EH1';

opCodeByte: OCTET (PLUS accessMode)?;

accessMode: 'rw' | 'rd';

INSTRUCTION_BEGIN: 'INSTRUCTION:';
D3_NOW_PREFIX: '0F 0F /r ';
VEX_PREFIX: 'VEX';
XOP_PREFIX: 'XOP';
EVEX_PREFIX: 'EVEX';
MVEX_PREFIX: 'MVEX';
OCTET: '<' [0-9A-F][0-9A-F] '>';
PLUS: '+';
DOT: '.';

WS: [ \n\t\r]+ -> skip;
