# cleanup.sh
echo "Cleaning project..."

# Remove all .class files recursively
find . -name "*.class" -delete

# Remove all .txt files recursively
find . -name "*.txt" -delete

# Remove empty directories (leaves src/ or other source folders intact)
find . -name "homes" -type d -delete

echo "Done!"