
# Encryption: Android vs. Python Tools

## Summary

- ✅ **Android App**: Encryption works perfectly (SQLCipher is integrated)
- ⚠️ **Python tools on Windows**: `pysqlcipher3` has installation issues
- ✅ **Solution**: Use `sqlcipher3-binary` or work without encryption

## Quickstart

### 1. Basic Installation (WITHOUT Encryption)

```bash
cd Python
.\setup.bat
```

That's it! The Python tools now work with **unencrypted** databases.

### 2. Add Encryption Support (Optional)

Only if you want to open encrypted databases from the Android app:

```bash
cd Python
venv\Scripts\activate
pip install sqlcipher3-binary
```

## How the Tools Work Together

### Android App ➡️ Python Tools

**Scenario 1: With Encryption**
1. Android app: Database encrypted with passphrase
2. Export: `.db` + `.sha256.json` files
3. Python tools: `sqlcipher3-binary` installed → Enter passphrase → Works! ✅

**Scenario 2: Without Encryption**
1. Android app: Encryption disabled (in settings)
2. Export: `.db` + `.sha256.json` files
3. Python tools: Works directly without extra installation ✅

**Scenario 3: Temporary Decryption**
1. Android app: Use encryption
2. Before export: Temporarily disable encryption
3. Export: Export unencrypted DB
4. Python tools: Process
5. After processing: Re-enable encryption


## Recommended: Use WSL and setup.sh for Encryption Support

For reliable encryption support on Windows, it is recommended to use the Windows Subsystem for Linux (WSL) and run the provided setup script:

### Step 1: Open WSL (Ubuntu)
Start Ubuntu (WSL) from your Start menu.

### Step 2: Navigate to your project folder
```bash
cd /mnt/d/Projects/AndroidStudioProjects/Android_WIFI_BT_GEOGRABBER/Python
```

### Step 3: Run the setup script
```bash
./setup.sh
```
This will automatically create a virtual environment and install all required dependencies, including SQLCipher and Python encryption support.

### Step 4: Test Installation
```bash
source venv/bin/activate
python scripts/database_encryption.py
```

Expected output:
- ✅ "Using sqlcipher3-binary for encryption support"
- ⚠️ "WARNING: No SQLCipher library found!" (only unencrypted DBs possible)

## Which Option Should I Choose?

### Use Encryption if:
- You collect sensitive WiFi/Bluetooth data
- You store the database on insecure devices
- You want to share the database with others (with checksum verification)
- You want maximum security

### Do NOT use Encryption if:
- You only do local analysis
- You need fast access
- You want to use the Python tools without extra installation
- `sqlcipher3-binary` installation causes problems

## Troubleshooting

### "Python is not installed or not in PATH!"
**Solved!** ✅ The setup script has been updated and now detects:
- `python` (default)
- `python3` (alternative)
- `py` (Windows launcher)

### "ERROR: Could not find a version that satisfies the requirement pysqlcipher3"
**Solution:** Use `sqlcipher3-binary` instead of `pysqlcipher3`:
```bash
pip install sqlcipher3-binary
```

### "error: Microsoft Visual C++ 14.0 or greater is required"
**Solution:** `pysqlcipher3` needs a C++ compiler. Instead, use:
```bash
pip install sqlcipher3-binary
```

### Python tools cannot open encrypted DB
**Options:**
1. Install `sqlcipher3-binary`: `pip install sqlcipher3-binary`
2. Export an unencrypted version from the Android app
3. Temporarily disable encryption for export

## Further Documentation

- **WINDOWS_ENCRYPTION_SETUP.md** - Detailed guide for Windows
- **ENCRYPTION_SUPPORT.md** - Technical details about encryption
- **requirements.txt** - Basic dependencies (without SQLCipher)

## Support

If you have problems:
1. Check if Python 3.8+ is installed
2. Run `setup.bat` again
3. For encrypted DBs: Install `sqlcipher3-binary`
4. Test with `python scripts\database_encryption.py`

## Summary of Changes

**What has changed:**
- ✅ `setup.bat` now correctly detects Python 3.10+ (`py`, `python3`, `python`)
- ✅ `requirements.txt` no longer contains problematic packages
- ✅ `database_encryption.py` automatically tries `sqlcipher3-binary` OR `pysqlcipher3`
- ✅ Python tools work WITHOUT SQLCipher with unencrypted DBs
- ✅ New documentation for Windows users

**Your next steps:**
1. Run `setup.bat` ✅ (now works!)
2. Optional: Install `sqlcipher3-binary` for encrypted DBs
3. Use the tools with or without encryption
