#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

echo "========================================================"
echo "      SmartSilk Local Engine - Clean Uninstaller        "
echo "========================================================"
echo "This will stop the local database and backend containers."
echo ""
read -r -p "Are you sure you want to stop and remove all local containers? (Y/N): " CONFIRM
if [ "$CONFIRM" != "Y" ] && [ "$CONFIRM" != "y" ]; then
    echo "Cancelled."
    exit 0
fi

if command -v docker >/dev/null 2>&1; then
    echo "Stopping and removing containers and networks..."
    docker compose down -v
fi

echo ""
read -r -p "Do you also want to delete all company database files in ./data? (Y/N): " DEL_DATA
if [ "$DEL_DATA" = "Y" ] || [ "$DEL_DATA" = "y" ]; then
    if [ -d "data" ]; then
        rm -rf data
        echo "Local database data removed."
    fi
fi

echo ""
echo "SmartSilk Local Engine has been cleanly removed."
