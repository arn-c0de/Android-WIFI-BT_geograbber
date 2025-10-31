@echo off
REM ========================================
REM Run Script for WiFi/BT GeoGrabber Python Tools
REM ========================================

echo.
echo ========================================
echo WiFi/BT GeoGrabber - Python Tools
echo ========================================
echo.

REM Check if venv exists
if not exist "venv" (
    echo [ERROR] Virtual environment not found!
    echo Please run setup.bat first to create the environment.
    echo.
    pause
    exit /b 1
)

REM Activate virtual environment
echo [INFO] Activating virtual environment...
call venv\Scripts\activate.bat
if %errorlevel% neq 0 (
    echo [ERROR] Failed to activate virtual environment!
    echo.
    pause
    exit /b 1
)
echo [SUCCESS] Virtual environment activated.
echo.

:menu
echo ========================================
echo Select a tool to run:
echo ========================================
echo.
echo [1] Map Viewer (Plot GUI)
echo [2] Database Combiner
echo [3] Exit
echo.
choice /C 123 /N /M "Enter your choice (1-3): "

if errorlevel 3 goto exit
if errorlevel 2 goto combine
if errorlevel 1 goto plot

:plot
echo.
echo [INFO] Starting Map Viewer (Plot GUI)...
echo ========================================
echo.
python start_plot_gui.py
goto end

:combine
echo.
echo [INFO] Starting Database Combiner...
echo ========================================
echo.
python start_combine_dbs.py
goto end

:exit
echo.
echo [INFO] Exiting...
goto end

:end
echo.
echo ========================================
echo [INFO] Tool finished. Deactivating environment...
echo ========================================
call deactivate
echo.
pause
exit /b 0
