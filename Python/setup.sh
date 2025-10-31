#!/bin/bash
# ========================================
# Setup Script for WiFi/BT GeoGrabber Python Tools (Linux)
# ========================================

set -e

echo "\n========================================"
echo "WiFi/BT GeoGrabber - Python Setup (Linux)"
echo "========================================\n"

# Check for Python
if ! command -v python3 &> /dev/null; then
    echo "[ERROR] Python3 is not installed!"
    echo "Please install Python 3.8 or higher."
    exit 1
fi

PYTHON=python3

echo "[INFO] Python found: $($PYTHON --version)"

# Check if venv exists
if [ -d "venv" ]; then
    read -p "Virtual environment already exists. Recreate? (y/N): " recreate
    if [[ "$recreate" =~ ^[Yy]$ ]]; then
        echo "[INFO] Removing existing virtual environment..."
        rm -rf venv
    fi
fi

# Create venv
echo "[INFO] Creating virtual environment..."
$PYTHON -m venv venv
source venv/bin/activate

echo "[INFO] Upgrading pip..."
pip install --upgrade pip
## Upgrade pip skipped for faster setup

# Install dependencies
echo "[INFO] Installing dependencies from requirements.txt..."
pip install -r requirements.txt

echo "\n========================================"
echo "[SUCCESS] Setup completed successfully!"
echo "========================================\n"
echo "Virtual environment is ready at: $(pwd)/venv"
echo "To run the tools, use: ./run.sh"
