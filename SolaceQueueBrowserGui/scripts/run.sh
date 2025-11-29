#!/bin/bash

# SolaceQueueBrowserGui Run Script
# Runs the application with a specified config file or default.json
# Supports master password for decrypting encrypted passwords

set -e  # Exit on any error

echo "=================================================="
echo "Starting SolaceQueueBrowserGui"
echo "=================================================="

# Change to the project directory (parent of scripts/)
cd "$(dirname "$0")/.."

# Parse command line arguments
CONFIG_FILE=""
MASTER_PASSWORD=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -c|--config)
            CONFIG_FILE="$2"
            shift 2
            ;;
        -mp|--master-password)
            MASTER_PASSWORD="$2"
            shift 2
            ;;
        --help|-h)
            echo ""
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  -c, --config FILE              Configuration file (default: config/default.json)"
            echo "  -mp, --master-password PWD     Master password for decrypting encrypted passwords"
            echo "  -h, --help                      Show this help message"
            echo ""
            echo "Examples:"
            echo "  # Run with default config:"
            echo "  $0"
            echo ""
            echo "  # Run with specific config:"
            echo "  $0 -c config/local-dev.json"
            echo ""
            echo "  # Run with master password (for encrypted passwords):"
            echo "  $0 -c config/default.json --master-password \"myMasterKey\""
            echo ""
            echo "Important Notes:"
            echo "  - Always quote the master password if it contains special characters"
            echo "  - Special characters like #, $, !, etc. must be quoted: --master-password \"pass#123\""
            echo "  - If decryption fails, verify you're using the same master password used for encryption"
            echo "  - Use interactive GUI prompt if command-line password handling is problematic"
            echo ""
            exit 0
            ;;
        *)
            # Legacy support: if first argument doesn't start with -, treat as config file
            if [ -z "$CONFIG_FILE" ] && [[ ! "$1" =~ ^- ]]; then
                CONFIG_FILE="$1"
                shift
            else
                echo "❌ Error: Unknown option: $1"
                echo "Use --help for usage information"
                exit 1
            fi
            ;;
    esac
done

# Set default config file if not provided
if [ -z "$CONFIG_FILE" ]; then
    CONFIG_FILE="config/default.json"
    echo "📄 Using default config: $CONFIG_FILE"
else
    echo "📄 Using provided config: $CONFIG_FILE"
fi

# Check if config file exists
if [ ! -f "$CONFIG_FILE" ]; then
    echo "❌ Error: Config file '$CONFIG_FILE' not found!"
    echo ""
    echo "Available config files:"
    ls -la config/*.json 2>/dev/null || echo "  No .json files found in config/"
    echo ""
    echo "Usage: $0 [options]"
    echo "  Use --help for detailed usage information"
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
if [ -n "$MASTER_PASSWORD" ]; then
    echo "   Master Password: [provided]"
fi
echo ""

# Build Java command arguments
JAVA_ARGS=("-jar" "$JAR_FILE" "-c" "$CONFIG_FILE")

# Add master password if provided
if [ -n "$MASTER_PASSWORD" ]; then
    JAVA_ARGS+=("--master-password" "$MASTER_PASSWORD")
fi

# Run the application
java "${JAVA_ARGS[@]}"