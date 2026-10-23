@echo off
setlocal
cd /d "%~dp0"

echo Stopping SmartSilk local services...
docker compose down
echo SmartSilk stopped cleanly.
pause
