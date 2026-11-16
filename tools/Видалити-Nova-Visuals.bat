@echo off
chcp 65001 >nul
echo Видаляю Nova Visuals повністю...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0uninstall-nova-visuals.ps1"
echo.
pause
