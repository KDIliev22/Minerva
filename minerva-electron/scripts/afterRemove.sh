#!/bin/bash
# Minerva Backend – Post-Remove Script (deb)
# Stops and removes the systemd service. Data directory is preserved.
set -e

systemctl stop minerva-backend.service 2>/dev/null || true
systemctl disable minerva-backend.service 2>/dev/null || true
rm -f /etc/systemd/system/minerva-backend.service
systemctl daemon-reload

echo "Minerva backend service removed."
echo "NOTE: User data in ~/.config/Minerva was NOT deleted."
