@echo off
setlocal
cd /d "%~dp0"

echo ========================================================
echo       SmartSilk Local Engine - Clean Uninstaller        
echo ========================================================
echo This will stop the local database and backend containers.
echo.
set /p CONFIRM="Are you sure you want to stop and remove all local containers? (Y/N): "
if /i not "%CONFIRM%"=="Y" (
    echo Cancelled.
    pause
    exit /b 0
)

where docker >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo Stopping and removing containers and networks...
    docker compose down -v
)

echo.
set /p DEL_DATA="Do you also want to delete all company database files in ./data? (Y/N): "
if /i "%DEL_DATA%"=="Y" (
    if exist "data" (
        rmdir /s /q "data" 2>nul
        echo Local database data removed.
    )
)

echo.
echo SmartSilk Local Engine has been removed.
pause
