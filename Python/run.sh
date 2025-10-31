#!/bin/bash
# ========================================
# Run Script for WiFi/BT GeoGrabber Python Tools (Linux)
# ========================================

set -e

echo "\n========================================"
echo "WiFi/BT GeoGrabber - Python Tools (Linux)"
echo "========================================\n"

# Check if venv exists
if [ ! -d "venv" ]; then
    echo "[ERROR] Virtual environment not found!"
    echo "Please run setup.sh first to create the environment."
    exit 1
fi

source venv/bin/activate

echo "[SUCCESS] Virtual environment activated."

while true; do
    echo "========================================"
    echo "Select a tool to run:"
    echo "========================================"
    echo "[1] Map Viewer (Plot GUI)"
    echo "[2] Database Combiner"
    echo "[3] Exit"
    read -p "Enter your choice (1-3): " choice
    case $choice in
        1)
            echo "\n[INFO] Starting Map Viewer (Plot GUI)..."
            $VIRTUAL_ENV/bin/python start_plot_gui.py
            ;;
        2)
            echo "\n[INFO] Starting Database Combiner..."
            $VIRTUAL_ENV/bin/python start_combine_dbs.py
            ;;
        3)
            echo "[INFO] Exiting..."
            deactivate
            break
            ;;
        *)
            echo "Invalid choice. Please enter 1, 2, or 3."
            ;;
    esac
done
