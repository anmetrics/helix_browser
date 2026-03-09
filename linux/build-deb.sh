#!/bin/bash
set -e

# ============================================
#  Helix Browser - Build .deb package
#  Usage: bash build-deb.sh
# ============================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="helix-browser"
APP_VERSION="3.0.0"
ARCH="amd64"
PKG_NAME="${APP_NAME}_${APP_VERSION}_${ARCH}"
BUILD_DIR="$SCRIPT_DIR/build/$PKG_NAME"

echo ""
echo "  Building Helix Browser v${APP_VERSION} .deb package..."
echo ""

# Clean previous build
rm -rf "$SCRIPT_DIR/build"
mkdir -p "$BUILD_DIR"

# --- Create directory structure ---
mkdir -p "$BUILD_DIR/DEBIAN"
mkdir -p "$BUILD_DIR/opt/helix-browser"
mkdir -p "$BUILD_DIR/usr/local/bin"
mkdir -p "$BUILD_DIR/usr/share/applications"
mkdir -p "$BUILD_DIR/usr/share/icons/hicolor/256x256/apps"

# --- Copy source files ---
cp "$SCRIPT_DIR/src/"*.py "$BUILD_DIR/opt/helix-browser/"
cp "$SCRIPT_DIR/src/style.css" "$BUILD_DIR/opt/helix-browser/"

# --- Create launcher script ---
cat > "$BUILD_DIR/usr/local/bin/helix-browser" << 'LAUNCHER'
#!/bin/bash
cd /opt/helix-browser && exec python3 main.py "$@"
LAUNCHER
chmod 755 "$BUILD_DIR/usr/local/bin/helix-browser"

# --- Create desktop entry ---
cat > "$BUILD_DIR/usr/share/applications/helix-browser.desktop" << 'DESKTOP'
[Desktop Entry]
Name=Helix Browser
Comment=Fast, Secure, Private Web Browser
Exec=helix-browser %u
Icon=helix-browser
Terminal=false
Type=Application
Categories=Network;WebBrowser;
MimeType=text/html;text/xml;application/xhtml+xml;x-scheme-handler/http;x-scheme-handler/https;
StartupNotify=true
Keywords=web;browser;internet;helix;
DESKTOP

# --- Create app icon (SVG) ---
cat > "$BUILD_DIR/usr/share/icons/hicolor/256x256/apps/helix-browser.svg" << 'SVG'
<?xml version="1.0" encoding="UTF-8"?>
<svg width="256" height="256" viewBox="0 0 256 256" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#141235"/>
      <stop offset="100%" stop-color="#0A091E"/>
    </linearGradient>
    <linearGradient id="helix" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#8B8BFF"/>
      <stop offset="100%" stop-color="#FF7EB3"/>
    </linearGradient>
  </defs>
  <rect width="256" height="256" rx="48" fill="url(#bg)"/>
  <circle cx="128" cy="128" r="70" fill="none" stroke="url(#helix)" stroke-width="12" opacity="0.3"/>
  <path d="M78 128 C78 88, 128 68, 128 128 S178 168, 178 128" fill="none" stroke="url(#helix)" stroke-width="14" stroke-linecap="round"/>
  <path d="M78 128 C78 168, 128 188, 128 128 S178 88, 178 128" fill="none" stroke="url(#helix)" stroke-width="14" stroke-linecap="round" opacity="0.6"/>
  <circle cx="128" cy="128" r="8" fill="#FFFFFF"/>
</svg>
SVG

# --- Create DEBIAN/control ---
cat > "$BUILD_DIR/DEBIAN/control" << CONTROL
Package: helix-browser
Version: ${APP_VERSION}
Section: web
Priority: optional
Architecture: ${ARCH}
Depends: python3 (>= 3.6), python3-gi, python3-gi-cairo, gir1.2-gtk-3.0, gir1.2-webkit2-4.0
Maintainer: Helix Browser Team <helix@example.com>
Description: Helix Browser - Fast, Secure, Private Web Browser
 A modern web browser built with GTK3 and WebKitGTK.
 Features include ad blocking, tracker protection, anti-fingerprinting,
 tab management, bookmarks, history, and more.
Homepage: https://github.com/nicenemo/helix-browser
Installed-Size: $(du -sk "$BUILD_DIR/opt" | cut -f1)
CONTROL

# --- Create post-install script ---
cat > "$BUILD_DIR/DEBIAN/postinst" << 'POSTINST'
#!/bin/bash
update-desktop-database /usr/share/applications/ 2>/dev/null || true
gtk-update-icon-cache /usr/share/icons/hicolor/ 2>/dev/null || true
POSTINST
chmod 755 "$BUILD_DIR/DEBIAN/postinst"

# --- Create post-remove script ---
cat > "$BUILD_DIR/DEBIAN/postrm" << 'POSTRM'
#!/bin/bash
update-desktop-database /usr/share/applications/ 2>/dev/null || true
if [ "$1" = "purge" ]; then
    rm -rf /opt/helix-browser
fi
POSTRM
chmod 755 "$BUILD_DIR/DEBIAN/postrm"

# --- Build .deb ---
cd "$SCRIPT_DIR/build"
dpkg-deb --build "$PKG_NAME"

# Move to linux/ root
mv "$SCRIPT_DIR/build/${PKG_NAME}.deb" "$SCRIPT_DIR/"

echo ""
echo "  ============================================"
echo "  BUILD SUCCESSFUL!"
echo ""
echo "  Package: $SCRIPT_DIR/${PKG_NAME}.deb"
echo ""
echo "  Install:  sudo dpkg -i ${PKG_NAME}.deb"
echo "  Fix deps: sudo apt-get install -f"
echo "  Run:      helix-browser"
echo "  Remove:   sudo apt remove helix-browser"
echo "  ============================================"
echo ""
