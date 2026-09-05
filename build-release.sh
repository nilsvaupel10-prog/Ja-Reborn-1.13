#!/bin/bash
# Fast release APK build script

set -e

echo "🔨 JA2 Reborn Release APK Builder"
echo "=================================="
echo ""

# Navigate to android directory
cd android

# Generate keystore if needed
echo "1️⃣  Setting up signing keystore..."
bash generate-keystore.sh
echo ""

# Clean build artifacts
echo "2️⃣  Cleaning previous builds..."
./gradlew clean --quiet
echo ""

# Build release APK
echo "3️⃣  Building release APK (this may take a few minutes)..."
./gradlew assembleRelease -x lint --quiet

# Find and display the output
APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
    echo ""
    echo "✅ BUILD SUCCESSFUL!"
    echo "=================================="
    echo "APK Location: $(pwd)/$APK_PATH"
    echo "APK Size: $APK_SIZE"
    echo ""
    echo "Version: $(cat ../version)"
else
    echo "❌ Build failed - APK not found"
    exit 1
fi
