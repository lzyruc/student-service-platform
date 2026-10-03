@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-local.ps1" %*
set "startup_exit=%ERRORLEVEL%"
if not "%startup_exit%"=="0" pause
exit /b %startup_exit%
