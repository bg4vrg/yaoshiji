@echo off
setlocal
cd /d "%~dp0"
echo [YaoShiJi] Using offline direct Android SDK build.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-apk-offline.ps1"
echo.
pause
