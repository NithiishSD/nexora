#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=========================================="
echo "    Starting SmartSilk Local Engine       "
echo "=========================================="

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "Error: Docker is required to run the local database and backend."
    echo "Please install Docker Desktop or Docker Engine first."
    exit 1
fi

echo "Starting local database and backend services..."
docker compose up -d

echo ""
echo "Waiting for SmartSilk local engine to become ready..."
for i in {1..30}; do
    if curl -s http://127.0.0.1:8088/actuator/health/readiness | grep -q "UP"; then
        echo ""
        echo "=========================================="
        echo "  SmartSilk Engine is LIVE and READY!    "
        echo "  Address: http://localhost:8088         "
        echo "=========================================="
        echo "You can now launch the SmartSilk desktop application."
        exit 0
    fi
    echo -n "."
    sleep 1
done

echo ""
echo "SmartSilk engine started in background. You can check status with: docker compose ps"
