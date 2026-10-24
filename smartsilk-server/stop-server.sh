#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "Stopping SmartSilk server engine containers..."
docker compose down
echo "SmartSilk server stopped successfully."
