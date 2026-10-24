@echo off
cd /d "%~dp0"

echo Stopping SmartSilk server engine containers...
docker compose down
echo SmartSilk server stopped successfully.
pause
