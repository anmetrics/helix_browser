#!/bin/bash

# Configuration
APP_NAME="HelixBrowser"
BUNDLE_ID="com.helix.browser.macos"
MACOS_DIR="macos/HelixBrowser"
BUILD_DIR="macos/build"
APP_BUNDLE="$BUILD_DIR/$APP_NAME.app"
EXECUTABLE_DIR="$APP_BUNDLE/Contents/MacOS"
RESOURCES_DIR="$APP_BUNDLE/Contents/Resources"
DMG_NAME="HelixBrowser.dmg"

echo "🚀 Starting builds for $APP_NAME..."

# 1. Create directory structure
mkdir -p "$BUILD_DIR"
mkdir -p "$EXECUTABLE_DIR"
mkdir -p "$RESOURCES_DIR"

# 2. Compile Swift files for both architectures
echo "📦 Compiling Swift sources for Universal Binary..."
SDK_PATH=$(xcrun --show-sdk-path --sdk macosx)
SWIFT_FILES=$(ls $MACOS_DIR/*.swift)

# Compile for arm64
echo "  - Building for arm64 (Apple Silicon)..."
swiftc -sdk "$SDK_PATH" \
    -target arm64-apple-macosx12.0 \
    -O -whole-module-optimization \
    -parse-as-library \
    $SWIFT_FILES \
    -o "$BUILD_DIR/$APP_NAME-arm64"

# Compile for x86_64
echo "  - Building for x86_64 (Intel)..."
swiftc -sdk "$SDK_PATH" \
    -target x86_64-apple-macosx12.0 \
    -O -whole-module-optimization \
    -parse-as-library \
    $SWIFT_FILES \
    -o "$BUILD_DIR/$APP_NAME-x86_64"

# Create Universal Binary using lipo
echo "  - Creating Universal Binary..."
lipo -create "$BUILD_DIR/$APP_NAME-arm64" "$BUILD_DIR/$APP_NAME-x86_64" -output "$EXECUTABLE_DIR/$APP_NAME"

if [ $? -ne 0 ]; then
    echo "❌ Compilation or Universal Binary creation failed."
    exit 1
fi

# 3. Copy Info.plist
echo "📝 Copying Info.plist..."
cp "$MACOS_DIR/Info.plist" "$APP_BUNDLE/Contents/Info.plist"

# 4. Create DMG
echo "💿 Creating DMG..."
if [ -f "$DMG_NAME" ]; then rm "$DMG_NAME"; fi

hdiutil create -volname "$APP_NAME" -srcfolder "$APP_BUNDLE" -ov -format UDZO "$DMG_NAME"

if [ $? -eq 0 ]; then
    echo "✅ Success! DMG created at ./$DMG_NAME"
else
    echo "❌ DMG creation failed."
    exit 1
fi
