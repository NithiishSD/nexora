@echo off
setlocal
cd /d "%~dp0"

echo ==========================================
echo    Reset SmartSilk Local Database         
echo ==========================================
echo WARNING: This will completely erase all local ledger data
echo and allow you to set up a brand new business from scratch!
echo.
set /p CONFIRM="Are you sure you want to proceed? (Type YES to confirm): "
if /i not "%CONFIRM%"=="YES" (
    echo Operation canceled.
    pause
    exit /b 0
)

echo.
echo Stopping local engine services...
docker compose down -v

if exist "data" (
    echo Removing old data directories...
    rmdir /s /q "data"
)

echo.
echo ==========================================
echo Database has been completely reset to clean state!
echo You can now run start.bat to start fresh.
echo ==========================================
pause
