@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo =============================================
echo    SmartSilk Business Server Engine Starter  
echo =============================================

docker --version >nul 2>&1
if errorlevel 1 (
    echo Error: Docker is not installed or not in PATH.
    echo Please install Docker Desktop from https://www.docker.com/products/docker-desktop/
    pause
    exit /b 1
)

if not exist .env (
    echo Creating .env from .env.example...
    copy .env.example .env >nul
)

echo Starting SmartSilk server engine containers...
docker compose up -d

echo =========================================================
echo    SUCCESS! SmartSilk Server started in the background.
echo =========================================================
echo  Local Access: http://localhost:8088
echo.
echo  In the SmartSilk Desktop App:
echo  Select 'Remote / Cloud Server' and enter your server address.
echo =========================================================
pause
