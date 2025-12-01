#!/bin/bash

# SolaceQueueBrowserGui Distribution Build Script
# Builds a clean runtime distribution package without source code or development files

set -e  # Exit on any error

echo "=================================================="
echo "Building SolaceQueueBrowserGui Runtime Distribution"
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

# Extract version from system.json first (needed for distribution package name)
APP_VERSION=$(./scripts/extract-version.sh)
if [ -z "$APP_VERSION" ]; then
    echo "⚠️  Warning: Could not extract version from system.json, using Maven version"
    APP_VERSION="1.0.0"
fi

echo "🔨 Building application JAR..."
mvn compile package -DskipTests

# Check if build was successful
if [ $? -eq 0 ]; then
    echo ""
    echo "📦 Renaming JAR file to use version from system.json (${APP_VERSION})..."
    
    # Rename JAR file to use app version
    DIST_DIR="target"
    OLD_JAR="${DIST_DIR}/SolaceQueueBrowserGui-1.0.0-jar-with-dependencies.jar"
    NEW_JAR="${DIST_DIR}/SolaceQueueBrowserGui-${APP_VERSION}-jar-with-dependencies.jar"
    
    if [ -f "$OLD_JAR" ]; then
        cp "$OLD_JAR" "$NEW_JAR"
        echo "   Renamed: $(basename "$OLD_JAR") → $(basename "$NEW_JAR")"
    else
        echo "⚠️  Warning: JAR file not found: $OLD_JAR"
        echo "   Build may have failed or JAR was not created"
        exit 1
    fi
    
    echo ""
    echo "📦 Building distribution package with version ${APP_VERSION}..."
    # Delete old distribution packages if they exist
    rm -f "${DIST_DIR}/SolaceQueueBrowserGui-1.0.0-runtime-distribution.zip" "${DIST_DIR}/SolaceQueueBrowserGui-1.0.0-runtime-distribution.tar.gz" 2>/dev/null || true
    rm -f "${DIST_DIR}/SolaceQueueBrowserGui-${APP_VERSION}-runtime-distribution.zip" "${DIST_DIR}/SolaceQueueBrowserGui-${APP_VERSION}-runtime-distribution.tar.gz" 2>/dev/null || true
    
    # Rebuild only the runtime-distribution assembly
    # Run validate and initialize phases to ensure app.version property is loaded
    mvn validate initialize package -DskipTests -Dmaven.assembly.skip.jar=true
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "📦 Renaming distribution packages to use version ${APP_VERSION}..."
        
        # Rename distribution packages to use app version
        OLD_ZIP="${DIST_DIR}/SolaceQueueBrowserGui-1.0.0-runtime-distribution.zip"
        NEW_ZIP="${DIST_DIR}/SolaceQueueBrowserGui-${APP_VERSION}-runtime-distribution.zip"
        OLD_TAR="${DIST_DIR}/SolaceQueueBrowserGui-1.0.0-runtime-distribution.tar.gz"
        NEW_TAR="${DIST_DIR}/SolaceQueueBrowserGui-${APP_VERSION}-runtime-distribution.tar.gz"
        
        if [ -f "$OLD_ZIP" ]; then
            mv "$OLD_ZIP" "$NEW_ZIP"
            echo "   Renamed: $(basename "$OLD_ZIP") → $(basename "$NEW_ZIP")"
        fi
        if [ -f "$OLD_TAR" ]; then
            mv "$OLD_TAR" "$NEW_TAR"
            echo "   Renamed: $(basename "$OLD_TAR") → $(basename "$NEW_TAR")"
        fi
        
        echo ""
        echo "✅ Build completed successfully!"
        echo ""
        
        # Find the distribution archives (version is read from system.json)
        ZIP_FILE=$(ls ${DIST_DIR}/SolaceQueueBrowserGui-*-runtime-distribution.zip 2>/dev/null | head -n 1)
        TAR_FILE=$(ls ${DIST_DIR}/SolaceQueueBrowserGui-*-runtime-distribution.tar.gz 2>/dev/null | head -n 1)
    
    if [ -n "$ZIP_FILE" ]; then
        echo "📦 Distribution packages created:"
        ls -lh "$ZIP_FILE"
        if [ -n "$TAR_FILE" ]; then
            ls -lh "$TAR_FILE"
        fi
        echo ""
        echo "Distribution package contents:"
        echo "  - Application JAR (with all dependencies)"
        echo "  - Required config files (system.json, log4j2.properties, icons)"
        echo "  - Runtime scripts (run.sh, crypt-util.sh)"
        echo "  - Documentation (README.md, USER_GUIDE.md)"
        echo ""
        echo "To extract and use:"
        echo "  unzip $ZIP_FILE"
        echo "  cd SolaceQueueBrowserGui-*/"
        echo "  cp config/sample-config.json config/default.json"
        echo "  # Edit config/default.json with your broker details"
        echo "  ./scripts/run.sh -c config/default.json"
        echo ""
    else
        echo "⚠️  Warning: Distribution package not found. Checking build output..."
        ls -la ${DIST_DIR}/*.zip ${DIST_DIR}/*.tar.gz 2>/dev/null || echo "  No distribution archives found"
    fi
    else
        echo "❌ Distribution package build failed!"
        exit 1
    fi
else
    echo "❌ Build failed!"
    exit 1
fi

