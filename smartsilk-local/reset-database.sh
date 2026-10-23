#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

echo "=========================================="
echo "   Reset SmartSilk Local Database        "
echo "=========================================="
echo "WARNING: This will completely erase all local ledger data"
echo "and allow you to set up a brand new business from scratch!"
echo ""
read -r -p "Are you sure you want to proceed? (Type YES to confirm): " CONFIRM
if [ "$CONFIRM" != "YES" ]; then
    echo "Operation canceled."
    exit 0
fi

echo "Stopping local engine services..."
docker compose down -v

if [ -d "data" ]; then
    echo "Removing old data directories..."
    rm -rf data
fi

echo ""
echo "=========================================="
echo "Database has been completely reset to clean state!"
echo "You can now run ./start.sh to start fresh."
echo "=========================================="
