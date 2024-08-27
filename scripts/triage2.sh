#!/usr/bin/env bash
mkdir -p good

# Loop over every xml file in the current directory
for file in *.xml; do
    # Skip the file named instructions.xml
    if [ "$file" == "instructions.xml" ]; then
        continue
    fi
    echo $file

    # Execute fuzz.sh script with the current file
    ./fuzz.sh "$file"
done
