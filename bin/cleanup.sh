#!/bin/bash
# cleanup.sh

# Change to the parent of the folder where this script lives
cd "$(dirname "$0")/.."

echo "Cleaning project..."

# Remove all .class files recursively
find . -name "*.class" -delete

# Remove all .txt files recursively
find . -name "*.txt" -delete

# Remove the homes folder and everything inside
if [ -d "homes" ]; then
    rm -rf "homes"
fi

echo "Done!"