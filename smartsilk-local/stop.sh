#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "Stopping SmartSilk local services..."
docker compose down
echo "SmartSilk local services stopped cleanly."
