#!/bin/bash

# Build script for NIOX Communication Plugin - Mobile Platforms
# Builds Android AAR and iOS XCFramework

set -e  # Exit on error

echo "======================================="
echo "NIOX Mobile SDK Build Script"
echo "Building Android AAR & iOS XCFramework"
echo "======================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if gradle wrapper exists
if [ ! -f "./gradlew" ]; then
    echo -e "${RED}Error: gradlew not found. Please run gradle wrapper first.${NC}"
    exit 1
fi

# Make gradlew executable
chmod +x ./gradlew

# Build Android AAR
echo -e "${BLUE}[1/2] Building Android AAR...${NC}"
./gradlew assembleRelease
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Android AAR built successfully${NC}"
    echo "   Location: build/outputs/aar/niox-communication-plugin-release.aar"
else
    echo -e "${RED}✗ Android AAR build failed${NC}"
    exit 1
fi
echo ""

# Build iOS XCFramework (only on macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo -e "${BLUE}[2/2] Building iOS XCFramework...${NC}"
    ./gradlew createReleaseXCFramework
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ iOS XCFramework built successfully${NC}"
        echo "   Location: build/XCFrameworks/release/NioxPlugin.xcframework"
    else
        echo -e "${RED}✗ iOS XCFramework build failed${NC}"
        exit 1
    fi
else
    echo -e "${YELLOW}[2/2] Skipping iOS XCFramework (macOS required)${NC}"
    echo -e "${YELLOW}   Current OS: $OSTYPE${NC}"
    echo -e "${YELLOW}   iOS builds can only be performed on macOS${NC}"
fi
echo ""

echo -e "${GREEN}======================================="
echo "Mobile SDK Build Complete!"
echo "=======================================${NC}"
echo ""
echo "Output files:"
echo "  📱 Android: build/outputs/aar/niox-communication-plugin-release.aar"
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "  🍎 iOS:     build/XCFrameworks/release/NioxPlugin.xcframework"
fi
echo ""
echo "Next steps:"
echo "  - Copy the AAR file to your Android project's libs/ folder"
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "  - Drag the XCFramework into your Xcode project"
fi
echo "  - See INTEGRATION_GUIDE.md for detailed integration instructions"
