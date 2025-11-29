#!/bin/bash

# SolaceQueueBrowserGui Password Encryption Script
# Wrapper script for encrypting/decrypting passwords using PasswordEncryptionCLI

set -e  # Exit on any error

# Change to the project directory (parent of scripts/)
cd "$(dirname "$0")/.."

# Find the JAR file
JAR_FILE=$(ls target/SolaceQueueBrowserGui-*-jar-with-dependencies.jar 2>/dev/null | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "❌ Error: JAR file not found in target/ directory"
    echo "Please run './scripts/build.sh' first to build the project"
    exit 1
fi

# Parse command line arguments
MODE="encrypt"
INTERACTIVE=true
PASSWORD=""
MASTER_KEY=""

# Check for help flag
if [ "$1" == "--help" ] || [ "$1" == "-h" ]; then
    echo ""
    echo "Usage: $0 [command] [options]"
    echo ""
    echo "Commands:"
    echo "  encrypt [password] [master-key]  - Encrypt a password"
    echo "  decrypt [encrypted] [master-key]  - Decrypt a password"
    echo "  --help, -h                        - Show this help message"
    echo ""
    echo "Examples:"
    echo "  # Encrypt (interactive mode - recommended, passwords hidden):"
    echo "  $0 encrypt"
    echo ""
    echo "  # Encrypt (non-interactive mode - for scripts):"
    echo "  $0 encrypt \"myPassword\" \"masterKey\""
    echo ""
    echo "  # Decrypt (interactive):"
    echo "  $0 decrypt"
    echo ""
    echo "  # Decrypt (non-interactive):"
    echo "  $0 decrypt \"ENC:AES256GCM:...\" \"masterKey\""
    echo ""
    echo "Note: Always quote passwords with special characters!"
    exit 0
fi

# Determine mode (encrypt or decrypt)
# Default to encrypt if no mode specified
if [ "$1" == "encrypt" ] || [ "$1" == "decrypt" ]; then
    MODE="$1"
    shift
fi

# Check if non-interactive mode (password and master key provided)
if [ $# -ge 2 ]; then
    INTERACTIVE=false
    PASSWORD="$1"
    MASTER_KEY="$2"
    
    # Run the CLI tool with provided arguments
    java -cp "$JAR_FILE" com.solace.psg.util.PasswordEncryptionCLI "$MODE" "$PASSWORD" "$MASTER_KEY"
else
    # Interactive mode
    # Run the CLI tool in interactive mode
    java -cp "$JAR_FILE" com.solace.psg.util.PasswordEncryptionCLI "$MODE"
fi
