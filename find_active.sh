#!/usr/bin/env bash

for file in dbg_thread_*
do
	CODE_SIZE=$(od --endian=little -N8 -t d8 -An $file | awk '{$1=$1};1')
	if [ $CODE_SIZE -ne 0 ]; then
		TEMP=`mktemp`
		dd if=$file of=$TEMP bs=8 skip=1 2>/dev/null
		echo "Disassembling possible perpetrator: $file"
		objdump -D -Mintel,x86-64 -b binary -m i386 $TEMP
		rm $TEMP
	fi
done
