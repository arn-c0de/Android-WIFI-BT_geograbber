@echo off
REM ========================================
REM Setup Script for WiFi/BT GeoGrabber Python Tools
REM ========================================

echo.
echo ========================================
echo WiFi/BT GeoGrabber - Python Setup
echo ========================================
echo.

echo [INFO] Python found:

REM Check if Python is installed (try python, python3, py)
set PYTHON_CMD=
python --version >nul 2>&1
if %errorlevel%==0 set PYTHON_CMD=python
if not defined PYTHON_CMD python3 --version >nul 2>&1
if %errorlevel%==0 set PYTHON_CMD=python3
if not defined PYTHON_CMD py --version >nul 2>&1
if %errorlevel%==0 set PYTHON_CMD=py
if not defined PYTHON_CMD (
    echo [ERROR] Python is not installed or not in PATH!
    echo Please install Python 3.8 or higher from https://www.python.org/
    echo.
    pause
    exit /b 1
)

echo [INFO] Python found:
%PYTHON_CMD% --version
echo.

REM Check if venv already exists
if exist "venv" (
    echo [INFO] Virtual environment already exists.
    choice /C YN /M "Do you want to recreate it? (Y=Yes, N=No)"
    if errorlevel 2 goto install_deps
    if errorlevel 1 (
        echo [INFO] Removing existing virtual environment...
        rmdir /s /q venv
    )
)

REM Create virtual environment
echo [INFO] Creating virtual environment...
%PYTHON_CMD% -m venv venv
if %errorlevel% neq 0 (
    echo [ERROR] Failed to create virtual environment!
    echo.
    pause
    exit /b 1
)
echo [SUCCESS] Virtual environment created.
echo.

:install_deps
REM Activate virtual environment
echo [INFO] Activating virtual environment...
call venv\Scripts\activate.bat
if %errorlevel% neq 0 (
    echo [ERROR] Failed to activate virtual environment!
    echo.
    pause
    exit /b 1
)

REM Upgrade pip (skipped for faster setup)

REM Install dependencies
echo [INFO] Installing dependencies from requirements.txt...
pip install -r requirements.txt
if %errorlevel% neq 0 (
    echo [ERROR] Failed to install dependencies!
    echo.
    pause
    exit /b 1
)
echo.

echo ========================================
echo [SUCCESS] Setup completed successfully!
echo ========================================@echo off
                                             REM ========================================
                                             REM Setup Script for WiFi/BT GeoGrabber Python Tools
                                             REM ========================================

                                             echo.
                                             echo ========================================
                                             echo WiFi/BT GeoGrabber - Python Setup
                                             echo ========================================
                                             echo.

                                             REM Check if Python is installed
                                             python --version >nul 2>&1
                                             if %errorlevel% neq 0 (
                                                 echo [ERROR] Python is not installed or not in PATH!
                                                 echo Please install Python 3.8 or higher from https://www.python.org/
                                                 echo.
                                                 pause
                                                 exit /b 1
                                             )

                                             echo [INFO] Python found:
                                             python --version
                                             echo.

                                             REM Check if venv already exists
                                             if exist "venv" (
                                                 echo [INFO] Virtual environment already exists.
                                                 choice /C YN /M "Do you want to recreate it? (Y=Yes, N=No)"
                                                 if errorlevel 2 goto install_deps
                                                 if errorlevel 1 (
                                                     echo [INFO] Removing existing virtual environment...
                                                     rmdir /s /q venv
                                                 )
                                             )

                                             REM Create virtual environment
                                             echo [INFO] Creating virtual environment...
                                             python -m venv venv
                                             if %errorlevel% neq 0 (
                                                 echo [ERROR] Failed to create virtual environment!
                                                 echo.
                                                 pause
                                                 exit /b 1
                                             )
                                             echo [SUCCESS] Virtual environment created.
                                             echo.

                                             :install_deps
                                             REM Activate virtual environment
                                             echo [INFO] Activating virtual environment...
                                             call venv\Scripts\activate.bat
                                             if %errorlevel% neq 0 (
                                                 echo [ERROR] Failed to activate virtual environment!
                                                 echo.
                                                 pause
                                                 exit /b 1
                                             )

                                             REM Upgrade pip (skipped for faster setup)

                                             REM Install dependencies
                                             echo [INFO] Installing dependencies from requirements.txt...
                                             pip install -r requirements.txt
                                             if %errorlevel% neq 0 (
                                                 echo [ERROR] Failed to install dependencies!
                                                 echo.
                                                 pause
                                                 exit /b 1
                                             )
                                             echo.

                                             echo ========================================
                                             echo [SUCCESS] Setup completed successfully!
                                             echo ========================================
                                             echo.
                                             echo Virtual environment is ready at: %CD%\venv
                                             echo.
                                             echo To run the tools, use: run.bat
                                             echo.
                                             pause

echo.
echo Virtual environment is ready at: %CD%\venv
echo.
echo To run the tools, use: run.bat
echo.
pause
