#!/bin/bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "Building SmartSilk local backend JAR..."
cd "$DIR/../Nexora-backend"
./gradlew bootJar
cp build/libs/app.jar "$DIR/app.jar"
echo "Success: smartsilk-local/app.jar is ready."
