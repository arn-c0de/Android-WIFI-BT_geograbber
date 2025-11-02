"""
Database Encryption Helper for SQLCipher encrypted databases
Compatible with Android SQLCipher implementation
"""
import sqlite3
import hashlib
import base64
from pathlib import Path

# Try multiple SQLCipher libraries for better Windows compatibility
SQLCIPHER_AVAILABLE = False
sqlcipher = None

# Try sqlcipher3-binary first (best for Windows)
try:
    import sqlcipher3.dbapi2 as sqlcipher
    SQLCIPHER_AVAILABLE = True
    print("Using sqlcipher3-binary for encryption support")
except ImportError:
    # Try pysqlcipher3 (works on Linux/Mac)
    try:
        from pysqlcipher3 import dbapi2 as sqlcipher
        SQLCIPHER_AVAILABLE = True
        print("Using pysqlcipher3 for encryption support")
    except ImportError:
        SQLCIPHER_AVAILABLE = False
        print("=" * 60)
        print("⚠️  WARNING: No SQLCipher library found!")
        print("=" * 60)
        print("Encrypted databases cannot be opened without SQLCipher.")
        print("")
        print("INSTALLATION OPTIONS:")
        print("")
        print("Windows (recommended):")
        print("  pip install sqlcipher3-binary")
        print("")
        print("Linux/Mac:")
        print("  pip install pysqlcipher3")
        print("")
        print("If installation fails, you can still use unencrypted databases.")
        print("=" * 60)


class DatabaseEncryption:
    """Handles encrypted and unencrypted SQLite databases"""
    
    def __init__(self, db_path):
        self.db_path = db_path
        self.conn = None
        self.is_encrypted = False
        self.passphrase = None
    
    def is_database_encrypted(self):
        """Check if database file is encrypted (SQLCipher format)"""
        try:
            with open(self.db_path, 'rb') as f:
                header = f.read(16)
                # SQLite unencrypted databases start with "SQLite format 3\x00"
                if header.startswith(b'SQLite format 3'):
                    return False
                # Encrypted databases have random-looking bytes
                return True
        except Exception as e:
            print(f"Error checking database encryption: {e}")
            return False
    
    def derive_key_from_passphrase(self, passphrase):
        """
        Derive database key from passphrase using PBKDF2
        Compatible with Android EncryptionManager implementation
        """
        # This should match the Android implementation's salt
        # For now, we'll use the passphrase directly as SQLCipher key
        # In production, you'd need to extract the salt from Android's secure storage
        return passphrase
    
    def connect_unencrypted(self):
        """Connect to unencrypted SQLite database"""
        try:
            self.conn = sqlite3.connect(self.db_path)
            self.is_encrypted = False
            return True
        except Exception as e:
            print(f"Error connecting to unencrypted database: {e}")
            return False
    
    def connect_encrypted(self, passphrase):
        """Connect to encrypted SQLCipher database"""
        if not SQLCIPHER_AVAILABLE:
            print("=" * 60)
            print("❌ ERROR: SQLCipher library not installed!")
            print("=" * 60)
            print("Encrypted databases cannot be opened.")
            print("")
            print("INSTALLATION:")
            print("  Windows: pip install sqlcipher3-binary")
            print("  Linux/Mac: pip install pysqlcipher3")
            print("=" * 60)
            return False
        
        try:
            # Derive key from passphrase
            key = self.derive_key_from_passphrase(passphrase)
            
            # Connect to encrypted database
            self.conn = sqlcipher.connect(self.db_path)
            
            # Set the key (this is the critical step)
            self.conn.execute(f"PRAGMA key = '{key}'")
            
            # Verify the key is correct by trying to access the database
            cursor = self.conn.cursor()
            cursor.execute("SELECT count(*) FROM sqlite_master")
            cursor.fetchone()
            
            self.is_encrypted = True
            self.passphrase = passphrase
            print(f"✓ Successfully opened encrypted database")
            return True
            
        except Exception as e:
            print(f"Error connecting to encrypted database: {e}")
            if self.conn:
                self.conn.close()
                self.conn = None
            return False
    
    def connect(self, passphrase=None):
        """
        Smart connect: Try encrypted first if passphrase provided,
        otherwise try unencrypted
        """
        # Check if database is encrypted
        if self.is_database_encrypted():
            print("Database appears to be encrypted (SQLCipher format)")
            
            if not passphrase:
                print("ERROR: Database is encrypted but no passphrase provided!")
                return False
            
            return self.connect_encrypted(passphrase)
        else:
            print("Database appears to be unencrypted (standard SQLite)")
            return self.connect_unencrypted()
    
    def get_connection(self):
        """Get the database connection"""
        return self.conn
    
    def close(self):
        """Close database connection"""
        if self.conn:
            self.conn.close()
            self.conn = None
    
    def __enter__(self):
        """Context manager entry"""
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit"""
        self.close()


def prompt_for_passphrase():
    """Prompt user for database passphrase"""
    import getpass
    
    print("\n" + "="*60)
    print("🔒 ENCRYPTED DATABASE DETECTED")
    print("="*60)
    print("This database is encrypted with SQLCipher.")
    print("Please enter the passphrase you set in the Android app.")
    print("="*60)
    
    passphrase = getpass.getpass("Passphrase: ")
    return passphrase


def open_database_with_gui_prompt(db_path):
    """
    Open database with GUI passphrase prompt if encrypted
    Returns connection or None
    """
    db_enc = DatabaseEncryption(db_path)
    
    if db_enc.is_database_encrypted():
        # Show GUI dialog for passphrase
        from tkinter import simpledialog, messagebox
        import tkinter as tk
        
        root = tk.Tk()
        root.withdraw()
        
        # Passphrase dialog
        passphrase = simpledialog.askstring(
            "Database Encrypted",
            "This database is encrypted.\nPlease enter your passphrase:",
            show='*'
        )
        
        root.destroy()
        
        if not passphrase:
            messagebox.showerror("Error", "No passphrase provided. Cannot open encrypted database.")
            return None
        
        if db_enc.connect_encrypted(passphrase):
            return db_enc.get_connection()
        else:
            messagebox.showerror("Error", "Wrong passphrase or corrupted database!")
            return None
    else:
        # Unencrypted database
        if db_enc.connect_unencrypted():
            return db_enc.get_connection()
        else:
            return None


def test_database_connection(db_path, passphrase=None):
    """Test if database can be opened with optional passphrase"""
    db_enc = DatabaseEncryption(db_path)
    
    print(f"\nTesting database: {Path(db_path).name}")
    print("-" * 60)
    
    if db_enc.connect(passphrase):
        conn = db_enc.get_connection()
        cursor = conn.cursor()
        
        # Get table info
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
        tables = [row[0] for row in cursor.fetchall()]
        
        print(f"✓ Database opened successfully")
        print(f"✓ Encryption: {'YES' if db_enc.is_encrypted else 'NO'}")
        print(f"✓ Tables found: {', '.join(tables)}")
        
        # Count records
        for table in tables:
            cursor.execute(f"SELECT COUNT(*) FROM {table}")
            count = cursor.fetchone()[0]
            print(f"  - {table}: {count} records")
        
        db_enc.close()
        return True
    else:
        print("✗ Failed to open database")
        return False


if __name__ == "__main__":
    # Test the encryption module
    import sys
    
    if len(sys.argv) > 1:
        db_path = sys.argv[1]
        passphrase = None
        
        if len(sys.argv) > 2:
            passphrase = sys.argv[2]
        else:
            # Check if encrypted
            db_enc = DatabaseEncryption(db_path)
            if db_enc.is_database_encrypted():
                passphrase = prompt_for_passphrase()
        
        test_database_connection(db_path, passphrase)
    else:
        print("Usage: python database_encryption.py <database_path> [passphrase]")
