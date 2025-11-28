#!/bin/bash

# SolaceQueueBrowserGui Run Script
# Runs the application with a specified config file or default.json

set -e  # Exit on any error

echo "=================================================="
echo "Starting SolaceQueueBrowserGui"
echo "=================================================="

# Change to the project directory (parent of scripts/)
cd "$(dirname "$0")/.."

# Configuration file handling
CONFIG_FILE=""
if [ $# -eq 0 ]; then
    # No arguments provided, use default config
    CONFIG_FILE="config/default.json"
    echo "📄 Using default config: $CONFIG_FILE"
else
    # Use provided config file
    CONFIG_FILE="$1"
    echo "📄 Using provided config: $CONFIG_FILE"
fi

# Check if config file exists
if [ ! -f "$CONFIG_FILE" ]; then
    echo "❌ Error: Config file '$CONFIG_FILE' not found!"
    echo ""
    echo "Available config files:"
    ls -la config/*.json 2>/dev/null || echo "  No .json files found in config/"
    echo ""
    echo "Usage: $0 [config-file]"
    echo "  If no config file is specified, config/default.json will be used"
    exit 1
fi

# Find the JAR file
JAR_FILE=$(ls target/SolaceQueueBrowserGui-*-jar-with-dependencies.jar 2>/dev/null | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "❌ Error: JAR file not found in target/ directory"
    echo "Please run './scripts/build.sh' first to build the project"
    exit 1
fi

echo "🚀 Starting application..."
echo "   JAR: $JAR_FILE"
echo "   Config: $CONFIG_FILE"
echo ""

# Run the application
java -jar "$JAR_FILE" -config "$CONFIG_FILE"