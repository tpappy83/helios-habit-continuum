@echo off
title HELIOS TWIN COMPANION BOOTSTRAPPER
color 0b

echo ========================================================
echo   HELIOS HABIT TRACKER - TWIN WINDOWS COMPANION CORE
echo ========================================================
echo.
echo [System] Bootstrapping companion runtime...
echo.

:: Check if Python is installed
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Python is not installed or not in your system PATH.
    echo Please install Python 3.8+ from https://www.python.org/downloads/
    echo Make sure to check "Add Python to PATH" during installation.
    echo.
    pause
    exit /b
)

echo [System] Installing and verifying required packages...
pip install -r requirements.txt
if %errorlevel% neq 0 (
    echo.
    echo [Warning] Some packages failed to install. 
    echo Companion will run in safe-fallback mode with core functionality.
    echo.
)

echo.
echo [System] Starting Helios Companion in active tracking mode...
echo.
python helios_companion.py

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Companion exited with errors.
    echo.
    pause
)
