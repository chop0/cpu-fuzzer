#!/usr/bin/env bash
mkdir -p good

# Loop over every xml file in the current directory
for file in *.xml; do
    # Skip the file named instructions.xml
    if [ "$file" == "instructions.xml" ]; then
        continue
    fi

    # Execute fuzz.sh script with the current file
    ./fuzz.sh "$file"

    # Check the exit status of the last command
    if [ $? -eq 1 ]; then
        # If exit code is 1, delete the file
        rm "$file"
    else
        # Otherwise, move it to the good folder
        mv "$file" good/
    fi
done
