@echo off
setlocal
cd /d "%~dp0"
echo ==========================================
echo    Building SmartSilk Local Backend JAR   
echo ==========================================
cd ..\Nexora-backend
call gradlew.bat bootJar
if %ERRORLEVEL% neq 0 (
    echo Error: Failed to build backend JAR.
    pause
    exit /b 1
)
copy /y build\libs\app.jar "..\smartsilk-local\app.jar"
echo.
echo ==========================================
echo  Success: smartsilk-local\app.jar is ready!
echo ==========================================
pause
