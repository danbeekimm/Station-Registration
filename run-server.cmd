@echo off
rem Windows launcher for cmd and PowerShell. Runs run-server.ps1 with the built-in Windows PowerShell.
rem Git Bash users: ./run-server.sh
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-server.ps1" %*
exit /b %ERRORLEVEL%
