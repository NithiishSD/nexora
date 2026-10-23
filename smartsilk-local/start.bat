@echo off
setlocal
cd /d "%~dp0"

echo ==========================================
echo     Starting SmartSilk Local Engine       
echo ==========================================

where docker >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Error: Docker Desktop is required to run the local database and backend.
    echo Please install Docker Desktop for Windows first.
    pause
    exit /b 1
)

echo Starting local database and backend services...
docker compose up -d

echo.
echo ==========================================
echo   SmartSilk Local Engine is RUNNING!      
echo   Address: http://localhost:8080          
echo ==========================================
echo You can now open the SmartSilk application!
echo.
pause
