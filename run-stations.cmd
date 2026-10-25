@echo off
rem Windows launcher for cmd and PowerShell. Runs run-stations.ps1 with the built-in Windows PowerShell.
rem Git Bash users: ./run-stations.sh
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-stations.ps1" %*
exit /b %ERRORLEVEL%
