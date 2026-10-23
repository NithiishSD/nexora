#!/bin/bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "========================================================"
echo "      SmartSilk Local Engine - Clean Uninstaller        "
echo "========================================================"
read -p "Are you sure you want to stop and remove local engine containers? (y/N): " confirm
if [[ ! "$confirm" =~ ^[yY]$ ]]; then
    echo "Cancelled."
    exit 0
fi

if command -v docker &> /dev/null; then
    docker compose down -v 2>/dev/null || true
    echo "Containers and networks removed."
fi

read -p "Do you also want to permanently delete local database files in ./data? (y/N): " del_data
if [[ "$del_data" =~ ^[yY]$ ]]; then
    rm -rf data/
    echo "Local database files removed."
fi

echo "SmartSilk Local Engine has been cleanly removed."
