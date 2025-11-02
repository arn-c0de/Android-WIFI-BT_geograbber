# Database Encryption Support for Python Tools

## Overview

The Python tools now support SQLCipher encrypted databases from the Android app!

## Requirements

### Windows Users (RECOMMENDED)

Use the pre-compiled binary package that works perfectly on Windows:

```bash
pip install sqlcipher3-binary
```

### Linux/Mac Users

```bash
pip install pysqlcipher3
```

### Installation Note

The `requirements.txt` file no longer includes SQLCipher by default, as it's **optional** and platform-specific. Install manually based on your OS (see above).

**Why?** `pysqlcipher3` has known installation issues on Windows. The Python tools work fine with **unencrypted** databases without any SQLCipher installation.

## Features

### 1. **Automatic Encryption Detection**
- Automatically detects if a database is encrypted
- Shows 🔒 icon when encrypted database is detected

### 2. **Passphrase Prompt**
- GUI dialog prompts for passphrase when opening encrypted databases
- Passphrase is verified before proceeding

### 3. **Transparent Operation**
- Works with both encrypted and unencrypted databases
- No code changes needed when switching between encrypted/unencrypted databases

## Usage

### Map Viewer (`start_plot_gui.py`)

1. Run the script: `python start_plot_gui.py`
2. Select your database file
3. If encrypted, enter your passphrase when prompted
4. Map will display normally

### Database Combiner (`start_combine_dbs.py`)

1. Run the script: `python start_combine_dbs.py`
2. Select main database (will prompt for passphrase if encrypted)
3. Add source databases (each will prompt for passphrase if encrypted)
4. Merge operations work transparently

## Encryption Module

The `scripts/database_encryption.py` module provides:

- `DatabaseEncryption` class for handling encrypted/unencrypted databases
- `is_database_encrypted()` - Check if database is encrypted
- `connect_encrypted()` - Connect with passphrase
- `connect_unencrypted()` - Connect to standard SQLite

### Testing Encryption

Test if a database is encrypted:

```bash
python scripts/database_encryption.py path/to/database.db
```

With passphrase:

```bash
python scripts/database_encryption.py path/to/database.db yourpassphrase
```

## Compatibility

### Android App Compatibility
- ✅ Compatible with SQLCipher 4.x (used in Android app)
- ✅ Supports same database format
- ✅ Works with databases exported from Android app

### Platform Support
| Platform | Package | Status |
|----------|---------|--------|
| Windows | `sqlcipher3-binary` | ✅ **Recommended** |
| Windows | `pysqlcipher3` | ❌ Often fails to install |
| Linux | `pysqlcipher3` | ✅ Works well |
| macOS | `pysqlcipher3` | ✅ Works well |

### Important Notes

1. **Passphrase Storage**: Passphrases are stored in memory only during operation
2. **Multiple Databases**: Each database can have a different passphrase
3. **Fallback**: If SQLCipher not installed, unencrypted databases still work perfectly
4. **Android Independence**: The Android app's encryption works independently - Python tools are optional

## Troubleshooting

### Windows: "pysqlcipher3 not found" or Installation Fails

**Solution:** Use `sqlcipher3-binary` instead (pre-compiled for Windows):
```bash
pip install sqlcipher3-binary
```

### "ERROR: Could not find a version that satisfies the requirement pysqlcipher3"

**Solution:** This is a known Windows issue. Use `sqlcipher3-binary`:
```bash
pip install sqlcipher3-binary
```

### "error: Microsoft Visual C++ 14.0 or greater is required"

**Solution:** Use the pre-compiled `sqlcipher3-binary` package:
```bash
pip install sqlcipher3-binary
```

### Still Can't Install SQLCipher?

**No Problem!** You have two options:

1. **Use unencrypted databases**: The Python tools work perfectly with unencrypted databases (no SQLCipher needed)
2. **Temporarily disable encryption**: In the Android app, disable encryption, export the DB, use Python tools, then re-enable encryption

See `WINDOWS_ENCRYPTION_SETUP.md` for detailed workflow options.

### "Wrong passphrase"
- Make sure you're using the correct passphrase from the Android app
- Passphrase is case-sensitive
- Try re-entering the passphrase

### "Database corrupted"
- The database file may be damaged
- Try exporting a new database from the Android app
- Verify checksum if available

## Security Notes

⚠️ **Important Security Information:**

- Passphrases are only stored in memory during operation
- Never share your passphrase
- Use strong passphrases (minimum 6 characters)
- Encrypted databases are much more secure than unencrypted ones

## Future Enhancements

- [ ] Option to convert unencrypted database to encrypted
- [ ] Batch processing with saved passphrases
- [ ] Keyfile support
- [ ] Integration with Android keystore exports

## Support

If you encounter issues with encrypted databases:

1. Verify the database is not corrupted (check file size > 0)
2. Ensure `pysqlcipher3` is installed correctly
3. Try opening the database in the Android app first
4. Check the logs for detailed error messages
