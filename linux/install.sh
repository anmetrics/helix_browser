#!/bin/bash
set -e

echo "╔══════════════════════════════════════╗"
echo "║     Helix Browser - Cài đặt Linux   ║"
echo "╚══════════════════════════════════════╝"

# Kiểm tra quyền root
if [ "$EUID" -ne 0 ]; then
    echo "[!] Cần chạy với quyền root: sudo bash install.sh"
    exit 1
fi

INSTALL_DIR="/opt/helix-browser"
DESKTOP_FILE="/usr/share/applications/helix-browser.desktop"
BIN_FILE="/usr/local/bin/helix-browser"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo ""
echo "[1/4] Cài đặt dependencies..."
apt update -qq
apt install -y -qq \
    python3 python3-pip python3-gi python3-gi-cairo \
    gir1.2-gtk-4.0 gir1.2-adw-1 \
    libwebkitgtk-6.0-dev 2>/dev/null

# Kiểm tra WebKit 6.0
if ! python3 -c "import gi; gi.require_version('WebKit', '6.0')" 2>/dev/null; then
    echo ""
    echo "[!] WebKit 6.0 không có sẵn trên hệ thống này."
    echo "    Cần Ubuntu 23.10+ hoặc 24.04+ để có WebKitGTK 6.0."
    echo "    Thử cài: sudo apt install gir1.2-webkit-6.0"
    echo ""
    # Thử cài gir1.2-webkit-6.0
    apt install -y -qq gir1.2-webkit-6.0 2>/dev/null || true
    if ! python3 -c "import gi; gi.require_version('WebKit', '6.0')" 2>/dev/null; then
        echo "[X] Không thể cài WebKit 6.0. Hủy cài đặt."
        exit 1
    fi
fi

echo ""
echo "[2/4] Copy files vào $INSTALL_DIR..."
mkdir -p "$INSTALL_DIR"
cp -r "$SCRIPT_DIR/src/"* "$INSTALL_DIR/"

echo ""
echo "[3/4] Tạo launcher script..."
cat > "$BIN_FILE" << 'EOF'
#!/bin/bash
cd /opt/helix-browser && exec python3 main.py "$@"
EOF
chmod +x "$BIN_FILE"

echo ""
echo "[4/4] Cài desktop entry..."
cat > "$DESKTOP_FILE" << EOF
[Desktop Entry]
Name=Helix Browser
Comment=Fast, Secure, Private Web Browser
Exec=helix-browser %u
Icon=web-browser
Terminal=false
Type=Application
Categories=Network;WebBrowser;
MimeType=text/html;text/xml;application/xhtml+xml;x-scheme-handler/http;x-scheme-handler/https;
StartupNotify=true
Keywords=web;browser;internet;helix;
EOF

# Cập nhật desktop database
update-desktop-database /usr/share/applications/ 2>/dev/null || true

echo ""
echo "══════════════════════════════════════"
echo "  Cài đặt thành công!"
echo ""
echo "  Chạy bằng lệnh:  helix-browser"
echo "  Hoặc tìm 'Helix Browser' trong app menu"
echo "══════════════════════════════════════"
