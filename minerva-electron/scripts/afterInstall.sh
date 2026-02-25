#!/bin/bash
# Minerva Backend – Post-Install Script (deb)
# Sets up the Java backend as a systemd service that starts on boot.
set -e

INSTALL_DIR="/opt/Minerva"

# Determine the real (non-root) user who invoked the installer
if [ -n "$SUDO_USER" ]; then
    REAL_USER="$SUDO_USER"
elif [ -n "$PKEXEC_UID" ]; then
    REAL_USER=$(getent passwd "$PKEXEC_UID" | cut -d: -f1)
else
    REAL_USER=$(logname 2>/dev/null || echo "$USER")
fi

# Fallback – never run the service as root
if [ "$REAL_USER" = "root" ] || [ -z "$REAL_USER" ]; then
    echo "WARNING: Could not determine non-root user. Service will not be installed."
    exit 0
fi

REAL_HOME=$(getent passwd "$REAL_USER" | cut -d: -f6)
DATA_DIR="$REAL_HOME/.config/Minerva"

# ── Create data directories ──
mkdir -p "$DATA_DIR/library" "$DATA_DIR/torrent_files" "$DATA_DIR/downloads" \
         "$DATA_DIR/album_art" "$DATA_DIR/uploads" "$DATA_DIR/torrents"
chown -R "$REAL_USER:$REAL_USER" "$DATA_DIR"

# ── Ensure bundled JRE is executable ──
chmod +x "$INSTALL_DIR/resources/backend/jre/bin/java" 2>/dev/null || true

# ── Write systemd unit ──
SERVICE_FILE=/etc/systemd/system/minerva-backend.service
JAVA_BIN="$INSTALL_DIR/resources/backend/jre/bin/java"
JAR_FILE="$INSTALL_DIR/resources/backend/minerva-backend.jar"

cat > "$SERVICE_FILE" << UNITEOF
[Unit]
Description=Minerva P2P Music Backend
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$REAL_USER
WorkingDirectory=$DATA_DIR
ExecStartPre=/bin/mkdir -p $DATA_DIR/library $DATA_DIR/torrent_files $DATA_DIR/downloads $DATA_DIR/album_art $DATA_DIR/uploads $DATA_DIR/torrents
ExecStart=$JAVA_BIN -jar $JAR_FILE
Environment=API_PORT=4567
Environment=SEARCH_PORT=4568
Environment=LISTEN_PORT=6881
Environment=LIBRARY_DIR=$DATA_DIR/library
Environment=TORRENT_DIR=$DATA_DIR/torrent_files
Environment=DOWNLOADS_DIR=$DATA_DIR/downloads
Environment=ALBUM_ART_DIR=$DATA_DIR/album_art
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
UNITEOF

# ── Enable & start ──
systemctl daemon-reload
systemctl enable minerva-backend.service
systemctl start minerva-backend.service || true

echo "Minerva backend service installed and started."
echo "  Data directory : $DATA_DIR"
echo "  Check status   : systemctl status minerva-backend"
