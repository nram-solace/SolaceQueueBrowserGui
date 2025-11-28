#!/bin/bash

# SolaceQueueBrowserGui Build Script
# Builds the project using Maven and creates the JAR with dependencies

set -e  # Exit on any error

echo "=================================================="
echo "Building SolaceQueueBrowserGui"
echo "=================================================="

# Change to the project directory (parent of scripts/)
cd "$(dirname "$0")/.."

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "❌ Error: Maven is not installed or not in PATH"
    echo "Please install Maven: https://maven.apache.org/install.html"
    exit 1
fi

# Clean and compile
echo "🧹 Cleaning previous builds..."
mvn clean

echo "🔨 Compiling and packaging..."
mvn compile package

# Check if build was successful
if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Build completed successfully!"
    echo ""
    echo "Generated files:"
    ls -la target/SolaceQueueBrowserGui-*.jar
    echo ""
    echo "To run the application:"
    echo "  ./scripts/run.sh [config-file]"
    echo ""
else
    echo "❌ Build failed!"
    exit 1
fi