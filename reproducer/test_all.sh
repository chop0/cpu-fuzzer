#!/usr/bin/env zsh

ASM_FILE=repro.S
EXEC_FILE=`mktemp`
LIB_O_FILE=`mktemp --suffix=.o`

gcc lib.c -c -o $LIB_O_FILE

echo "nop_count, nop_1_byte, nop_2_byte, nop_3_byte"

for ((i = 0; i < 32; i++)); do
  				gcc -DNOP_COUNT=$i -DNOP_SIZE=1 -nostartfiles $ASM_FILE $LIB_O_FILE -o $EXEC_FILE
  				$EXEC_FILE
  				SUCCESS_1=$?
  				SUCCESS_1=$((SUCCESS_1 == 0))

          gcc -DNOP_COUNT=$i -DNOP_SIZE=2 -nostartfiles $ASM_FILE $LIB_O_FILE -o $EXEC_FILE
          $EXEC_FILE
          SUCCESS_2=$?
          SUCCESS_2=$((SUCCESS_2 == 0))

          gcc -DNOP_COUNT=$i -DNOP_SIZE=3 -nostartfiles $ASM_FILE $LIB_O_FILE -o $EXEC_FILE
          $EXEC_FILE
          SUCCESS_3=$?
          SUCCESS_3=$((SUCCESS_3 == 0))

          echo "$i, $SUCCESS_1, $SUCCESS_2, $SUCCESS_3"
done

rm $EXEC_FILE $LIB_O_FILE