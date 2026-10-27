@echo off
rem Stops the ngrok tunnel, backend, frontend, and local PostgreSQL for this project.
rem Pass -DryRun to only list what would be stopped.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop-bank.ps1" %*
