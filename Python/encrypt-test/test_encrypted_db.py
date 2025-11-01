#!/usr/bin/env python3
"""
SQLCipher Database Key Testing Tool
====================================
This script helps debug encrypted database import issues by:
1. Loading the encryption salt from metadata (.sha256.json)
2. Deriving the encryption key using PBKDF2 (same as Android app)
3. Testing if the key can open the encrypted database
4. Showing detailed debug information

Requirements:
    pip install pysqlcipher3
    OR
    pip install sqlcipher3

Usage:
    python test_encrypted_db.py
"""

import os
import sys
import json
import base64
import os
import sys
import json
import base64
import hashlib
from getpass import getpass
try:
    import sqlcipher3 as sqlite3
except ImportError:
    try:
        import pysqlcipher3 as sqlite3
    except ImportError:
        print("ERROR: You need to install sqlcipher3 or pysqlcipher3 (pip install sqlcipher3)")
        sys.exit(1)

def get_script_dir():
    return os.path.dirname(os.path.abspath(__file__))

def input_file(prompt):
    script_dir = get_script_dir()
    while True:
        path = input(prompt).strip()
        if not path:
            print("Please enter a filename.")
            continue
        # Always look in script dir if not absolute
        if not os.path.isabs(path):
            path = os.path.join(script_dir, path)
        if os.path.isfile(path):
            return path
        print(f"❌ ERROR: File not found: {os.path.basename(path)} (looked in {script_dir})")

def main():
    print("SQLCipher Database Key Testing Tool")
    print("="*70)
    print("  This tool helps debug encrypted database import issues\n  by testing if your passphrase and metadata file are correct.\n")
    print("="*70)
    print("  Step 1: Select Database File\n" + "="*70)
    db_path = input_file("  Enter filename of encrypted .db file (in this folder): ")
    print("  Step 2: Select Metadata JSON File\n" + "="*70)
    json_path = input_file("  Enter filename of metadata .json file (in this folder): ")
    # ...existing code...
    """
    Load encryption metadata from .sha256.json file
    
    Returns:
        dict: Metadata containing salt, checksum, etc.
    """
    print_header("Loading Metadata File")
    
    if not os.path.exists(metadata_path):
        print(f"  ❌ ERROR: Metadata file not found: {metadata_path}")
        return None
    
    try:
        with open(metadata_path, 'r', encoding='utf-8') as f:
            metadata = json.load(f)
        
        print_info("File", os.path.basename(metadata_path))
        print_info("Version", metadata.get('version', 'N/A'))
        print_info("Algorithm", metadata.get('algorithm', 'N/A'))
        print_info("Database File", metadata.get('filename', 'N/A'))
        print_info("File Size", f"{metadata.get('fileSize', 0)} bytes")
        print_info("Encrypted", "Yes" if metadata.get('encrypted') else "No")
        
        if 'encryptionSalt' in metadata:
            salt_b64 = metadata['encryptionSalt']
            salt_bytes = base64.b64decode(salt_b64)
            print_info("Salt (Base64)", salt_b64[:40] + "...")
            print_info("Salt (Hex)", binascii.hexlify(salt_bytes).decode('ascii')[:40] + "...")
            print_info("Salt Length", f"{len(salt_bytes)} bytes")
        else:
            print("  ⚠️  WARNING: No encryption salt found in metadata!")
            return None
        
        return metadata
        
    except json.JSONDecodeError as e:
        print(f"  ❌ ERROR: Invalid JSON in metadata file: {e}")
        return None
    except Exception as e:
        print(f"  ❌ ERROR: Failed to load metadata: {e}")
        return None

def test_database_with_key(db_path, hex_key):
    """
    Try to open the database with the derived key
    
    Args:
        db_path (str): Path to encrypted database
        hex_key (str): Hex string of encryption key (64 chars)
    
    Returns:
        bool: True if database opens successfully
    """
    print_header("Testing Database Access")
    
    if not os.path.exists(db_path):
        print(f"  ❌ ERROR: Database file not found: {db_path}")
        return False
    
    file_size = os.path.getsize(db_path)
    print_info("Database File", os.path.basename(db_path))
    print_info("File Size", f"{file_size} bytes")
    
    # Try to import SQLCipher
    try:
        from pysqlcipher3 import dbapi2 as sqlcipher
        print_info("SQLCipher Library", "pysqlcipher3 ✓")
    except ImportError:
        try:
            import sqlcipher3.dbapi2 as sqlcipher
            print_info("SQLCipher Library", "sqlcipher3 ✓")
        except ImportError:
            print("  ❌ ERROR: SQLCipher library not installed!")
            print("\n  Install with:")
            print("    pip install pysqlcipher3")
            print("  OR")
            print("    pip install sqlcipher3")
            return False
    
    # Format key for SQLCipher: x'<hexstring>'
    sqlcipher_key = f"x'{hex_key}'"
    print_info("Key Format", "x'<hex>' (SQLCipher)")
    print_info("Key Length", f"{len(hex_key)} characters (hex)")
    print_info("Key Preview", hex_key[:16] + "..." + hex_key[-16:])
    
    # Try to open the database
    print("\n  🔑 Attempting to open database...")
    try:
        conn = sqlcipher.connect(db_path)
        cursor = conn.cursor()
        
        # Set the encryption key
        cursor.execute(f"PRAGMA key = \"{sqlcipher_key}\";")
        
        # Try to read from the database
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table';")
        tables = cursor.fetchall()
        
        print("  ✅ SUCCESS! Database opened successfully!")
        print("\n  📋 Tables found:")
        for table in tables:
            print(f"     - {table[0]}")
            
            # Count records in each table
            try:
                cursor.execute(f"SELECT COUNT(*) FROM {table[0]}")
                count = cursor.fetchone()[0]
                print(f"       ({count} records)")
            except:
                pass
        
        conn.close()
        return True
        
    except Exception as e:
        print(f"  ❌ FAILED! Could not open database")
        print(f"  Error: {str(e)}")
        print("\n  Possible reasons:")
        print("    • Wrong passphrase")
        print("    • Incorrect salt (metadata file doesn't match database)")
        print("    • Database is corrupted")
        print("    • Database was encrypted with different parameters")
        return False

def test_different_iterations(db_path, passphrase, salt):
    """
    Test database with different iteration counts
    SQLCipher has changed defaults over versions
    """
    print_header("Testing Different PBKDF2 Iterations")
    
    iteration_counts = [
        (64000, "SQLCipher 3.x default"),
        (256000, "SQLCipher 4.x default"),
        (10000, "Old/custom value"),
        (4000, "Very old SQLCipher"),
    ]
    
    for iterations, description in iteration_counts:
        print(f"\n  Testing {iterations} iterations ({description})...")
        hex_key = derive_key_pbkdf2(passphrase, salt, iterations=iterations)
        
        if test_database_with_key(db_path, hex_key):
            print(f"\n  ✅ SUCCESS with {iterations} iterations!")
            return iterations
    
    return None

def main():
    """Main function"""
    print_header("SQLCipher Database Key Testing Tool")
    print("  This tool helps debug encrypted database import issues")
    print("  by testing if your passphrase and metadata file are correct.")
    
    # Step 1: Select database file
    print_header("Step 1: Select Database File")
    db_path = input("  Enter path to encrypted .db file: ").strip().strip('"').strip("'")
    
    if not os.path.exists(db_path):
        print(f"  ❌ ERROR: File not found: {db_path}")
        return 1
    
    # Step 2: Select metadata file
    print_header("Step 2: Select Metadata File")
    
    # Try to auto-detect metadata file
    auto_metadata = db_path.replace('.db', '.sha256.json')
    if os.path.exists(auto_metadata):
        print(f"  Found metadata file: {os.path.basename(auto_metadata)}")
        use_auto = input("  Use this file? (Y/n): ").strip().lower()
        if use_auto in ['', 'y', 'yes']:
            metadata_path = auto_metadata
        else:
            metadata_path = input("  Enter path to .sha256.json file: ").strip().strip('"').strip("'")
    else:
        metadata_path = input("  Enter path to .sha256.json file: ").strip().strip('"').strip("'")
    
    # Load metadata
    metadata = load_metadata_file(metadata_path)
    if not metadata or 'encryptionSalt' not in metadata:
        print("\n  ❌ Cannot proceed without valid metadata file with encryption salt")
        return 1
    
    # Decode salt
    salt_b64 = metadata['encryptionSalt']
    salt_bytes = base64.b64decode(salt_b64)
    
    # Step 3: Enter passphrase
    print_header("Step 3: Enter Passphrase")
    passphrase = getpass("  Enter passphrase: ")
    
    if len(passphrase) < 6:
        print("  ⚠️  WARNING: Passphrase is very short (< 6 characters)")
    
    # Step 4: Derive key
    print_header("Step 4: Key Derivation")
    print_info("Algorithm", "PBKDF2-HMAC-SHA1")
    print_info("Iterations", "64000 (SQLCipher 3.x default)")
    print_info("Key Length", "256 bits (32 bytes)")
    
    hex_key = derive_key_pbkdf2(passphrase, salt_bytes, iterations=64000)
    print_info("Derived Key (Hex)", hex_key[:32] + "...")
    
    # Step 5: Test database
    success = test_database_with_key(db_path, hex_key)
    
    if not success:
        print("\n  Trying different iteration counts...")
        working_iterations = test_different_iterations(db_path, passphrase, salt_bytes)
        
        if working_iterations:
            print_header("Solution Found!")
            print(f"  The database works with {working_iterations} PBKDF2 iterations")
            print(f"  You need to update your Android app to use {working_iterations} iterations")
        else:
            print_header("Debugging Tips")
            print("  1. Verify you're using the EXACT passphrase from the Android app")
            print("  2. Make sure the .sha256.json file was exported WITH the database")
            print("  3. Check if the database and metadata are from the same export")
            print("  4. Try exporting the database again from the Android app")
            return 1
    else:
        print_header("Success!")
        print("  ✅ Your passphrase and metadata file are CORRECT")
        print("  ✅ The Android app should be able to import this database")
        print("\n  If import still fails in the Android app, there may be an")
        print("  issue with how the app formats the key for SQLCipher.")
        return 0

if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n\n  Aborted by user.")
        sys.exit(1)
    except Exception as e:
        print(f"\n  ❌ Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
