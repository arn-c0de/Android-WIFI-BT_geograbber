import sqlite3
import sys
import tkinter as tk
from tkinter import filedialog, messagebox, ttk, simpledialog
import os
import shutil
import math
import hashlib
import json
from datetime import datetime

# Add scripts directory to path for imports
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'scripts'))

# Import encryption helper
try:
    from database_encryption import DatabaseEncryption, SQLCIPHER_AVAILABLE
    ENCRYPTION_SUPPORT = True
except ImportError:
    ENCRYPTION_SUPPORT = False
    print("Warning: Database encryption module not found. Encrypted databases cannot be opened.")

class DatabaseCombiner:
    def __init__(self):
        self.main_db_path = None
        self.source_db_paths = []
        self.main_db_passphrase = None  # Store passphrase for main DB
        self.source_db_passphrases = {}  # Store passphrases for source DBs {path: passphrase}
        self.setup_gui()
    
    def setup_gui(self):
        """Creates the GUI for database merging"""
        self.root = tk.Tk()
        self.root.title("WiFi/Bluetooth Database Combiner")
        self.root.geometry("800x600")
        self.root.resizable(True, True)

        # Dark Mode Colors
        self.colors = {
            'bg': '#1e1e1e',
            'fg': '#e0e0e0',
            'bg_dark': '#2b2b2b',
            'bg_lighter': '#3c3c3c',
            'accent': '#0d7377',
            'accent_hover': '#14a098',
            'text': '#ffffff',
            'text_muted': '#a0a0a0',
            'border': '#404040'
        }

        # Configure root background
        self.root.configure(bg=self.colors['bg'])

        # Configure ttk styles
        style = ttk.Style()
        style.theme_use('clam')

        style.configure('TFrame', background=self.colors['bg'])
        style.configure('TLabel', background=self.colors['bg'], foreground=self.colors['fg'])
        style.configure('TButton', background=self.colors['accent'], foreground=self.colors['text'],
                       borderwidth=1, focuscolor='none', relief='flat')
        style.map('TButton', background=[('active', self.colors['accent_hover'])])
        style.configure('TCheckbutton', background=self.colors['bg'], foreground=self.colors['fg'])
        style.configure('TEntry', fieldbackground=self.colors['bg_lighter'], foreground=self.colors['text'],
                       bordercolor=self.colors['border'])
        style.configure('TLabelframe', background=self.colors['bg'], foreground=self.colors['fg'],
                       bordercolor=self.colors['border'])
        style.configure('TLabelframe.Label', background=self.colors['bg'], foreground=self.colors['fg'])
        style.configure('TProgressbar', background=self.colors['accent'], troughcolor=self.colors['bg_dark'])

        # Main frame
        main_frame = ttk.Frame(self.root, padding="10")
        main_frame.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        # Title
        title_label = ttk.Label(main_frame, text="Database Merge",
                               font=("Arial", 16, "bold"))
        title_label.grid(row=0, column=0, columnspan=3, pady=(0, 20))
        
        # Main Database Section
        ttk.Label(main_frame, text="Main Database (Target):",
                 font=("Arial", 12, "bold")).grid(row=1, column=0, sticky=tk.W, pady=(0, 5))
        
        self.main_db_var = tk.StringVar()
        main_db_entry = ttk.Entry(main_frame, textvariable=self.main_db_var, width=60)
        main_db_entry.grid(row=2, column=0, columnspan=2, sticky=(tk.W, tk.E), pady=(0, 5))
        
        ttk.Button(main_frame, text="Select",
                  command=self.select_main_db).grid(row=2, column=2, padx=(5, 0), pady=(0, 5))
        
        # Source Databases Section
        ttk.Label(main_frame, text="Source Databases (add):",
                 font=("Arial", 12, "bold")).grid(row=3, column=0, sticky=tk.W, pady=(20, 5))
        
        # Listbox for Source DBs
        self.source_listbox = tk.Listbox(main_frame, height=6, width=80,
                                         bg=self.colors['bg_lighter'], fg=self.colors['text'],
                                         selectbackground=self.colors['accent'],
                                         selectforeground=self.colors['text'],
                                         highlightthickness=1, highlightbackground=self.colors['border'],
                                         borderwidth=0)
        self.source_listbox.grid(row=4, column=0, columnspan=2, sticky=(tk.W, tk.E), pady=(0, 5))

        # Scrollbar for Listbox
        scrollbar = ttk.Scrollbar(main_frame, orient=tk.VERTICAL, command=self.source_listbox.yview)
        scrollbar.grid(row=4, column=2, sticky=(tk.N, tk.S), pady=(0, 5))
        self.source_listbox.config(yscrollcommand=scrollbar.set)
        
        # Buttons for Source DBs
        source_buttons_frame = ttk.Frame(main_frame)
        source_buttons_frame.grid(row=5, column=0, columnspan=3, pady=(5, 0))

        ttk.Button(source_buttons_frame, text="Add DB",
                  command=self.add_source_db).pack(side=tk.LEFT, padx=(0, 5))
        ttk.Button(source_buttons_frame, text="Remove Selected",
                  command=self.remove_source_db).pack(side=tk.LEFT, padx=(0, 5))
        ttk.Button(source_buttons_frame, text="Remove All",
                  command=self.clear_source_dbs).pack(side=tk.LEFT)
        
        # Options
        options_frame = ttk.LabelFrame(main_frame, text="Merge Options", padding="10")
        options_frame.grid(row=6, column=0, columnspan=3, sticky=(tk.W, tk.E), pady=(20, 0))

        self.backup_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(options_frame, text="Create backup of Main DB",
                       variable=self.backup_var).grid(row=0, column=0, sticky=tk.W, pady=(0, 5))

        self.signal_priority_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(options_frame, text="Prefer better signal strength",
                       variable=self.signal_priority_var).grid(row=1, column=0, sticky=tk.W, pady=(0, 5))

        self.coordinate_update_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(options_frame, text="Update coordinates with better signal",
                       variable=self.coordinate_update_var).grid(row=2, column=0, sticky=tk.W, pady=(0, 5))
        
        # Progress Bar
        self.progress_var = tk.DoubleVar()
        self.progress_bar = ttk.Progressbar(main_frame, variable=self.progress_var, 
                                          maximum=100, length=400)
        self.progress_bar.grid(row=7, column=0, columnspan=3, sticky=(tk.W, tk.E), pady=(20, 5))
        
        # Status Label
        self.status_var = tk.StringVar(value="Ready to start...")
        self.status_label = ttk.Label(main_frame, textvariable=self.status_var)
        self.status_label.grid(row=8, column=0, columnspan=3, pady=(0, 10))
        
        # Action Buttons
        action_frame = ttk.Frame(main_frame)
        action_frame.grid(row=9, column=0, columnspan=3, pady=(10, 0))
        
        ttk.Button(action_frame, text="🔍 Start Analysis",
                  command=self.analyze_databases).pack(side=tk.LEFT, padx=(0, 10))
        ttk.Button(action_frame, text="🧹 Clean DB",
                  command=self.clean_database).pack(side=tk.LEFT, padx=(0, 10))
        ttk.Button(action_frame, text="🔧 Repair DB",
                  command=self.repair_database).pack(side=tk.LEFT, padx=(0, 10))
        ttk.Button(action_frame, text="🚀 Merge",
                  command=self.combine_databases).pack(side=tk.LEFT, padx=(0, 10))
        ttk.Button(action_frame, text="❌ Exit",
                  command=self.root.quit).pack(side=tk.LEFT)
        
        # Log Text Area
        log_frame = ttk.LabelFrame(main_frame, text="Log", padding="5")
        log_frame.grid(row=10, column=0, columnspan=3, sticky=(tk.W, tk.E, tk.N, tk.S), pady=(20, 0))

        self.log_text = tk.Text(log_frame, height=8, width=80,
                               bg=self.colors['bg_dark'], fg=self.colors['text'],
                               insertbackground=self.colors['text'],
                               selectbackground=self.colors['accent'],
                               selectforeground=self.colors['text'],
                               highlightthickness=1, highlightbackground=self.colors['border'],
                               borderwidth=0)
        self.log_text.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))

        log_scrollbar = ttk.Scrollbar(log_frame, orient=tk.VERTICAL, command=self.log_text.yview)
        log_scrollbar.grid(row=0, column=1, sticky=(tk.N, tk.S))
        self.log_text.config(yscrollcommand=log_scrollbar.set)
        
        # Grid weights
        self.root.columnconfigure(0, weight=1)
        self.root.rowconfigure(0, weight=1)
        main_frame.columnconfigure(0, weight=1)
        main_frame.rowconfigure(10, weight=1)
        log_frame.columnconfigure(0, weight=1)
        log_frame.rowconfigure(0, weight=1)
    
    def log(self, message):
        """Adds a message to the log"""
        timestamp = datetime.now().strftime("%H:%M:%S")
        self.log_text.insert(tk.END, f"[{timestamp}] {message}\n")
        self.log_text.see(tk.END)
        self.root.update()
    
    def open_database_connection(self, db_path, passphrase=None):
        """Opens a database connection (encrypted or unencrypted)"""
        if not ENCRYPTION_SUPPORT:
            # Fallback to standard SQLite
            return sqlite3.connect(db_path)
        
        db_enc = DatabaseEncryption(db_path)
        
        if db_enc.is_database_encrypted():
            self.log(f"🔒 Database is encrypted: {os.path.basename(db_path)}")
            
            # Check if passphrase provided
            if not passphrase:
                passphrase = simpledialog.askstring(
                    "Database Encrypted",
                    f"Database is encrypted:\n{os.path.basename(db_path)}\n\nPlease enter passphrase:",
                    show='*'
                )
                
                if not passphrase:
                    self.log(f"❌ No passphrase provided for {os.path.basename(db_path)}")
                    return None
            
            # Try to connect
            if db_enc.connect_encrypted(passphrase):
                self.log(f"✓ Encrypted database opened: {os.path.basename(db_path)}")
                return db_enc.get_connection()
            else:
                self.log(f"❌ Wrong passphrase for {os.path.basename(db_path)}")
                return None
        else:
            # Unencrypted database
            if db_enc.connect_unencrypted():
                return db_enc.get_connection()
            else:
                return None
    
    def select_main_db(self):
        """Selects the Main Database"""
        initial_dir = os.path.dirname(os.path.abspath(__file__))
        file_path = filedialog.askopenfilename(
            title="Select Main Database",
            initialdir=initial_dir,
            filetypes=[("Database files", "*.db"), ("All files", "*.*")]
        )

        if file_path:
            # Check if checksum metadata file exists in same directory
            checksum_path = file_path + ".sha256.json"
            if os.path.exists(checksum_path):
                # Ask user if they want to verify with the found checksum file
                verify = messagebox.askyesno(
                    "Checksum Verification Available",
                    f"A checksum metadata file was found:\n{os.path.basename(checksum_path)}\n\n"
                    "Would you like to verify the database integrity?"
                )
                
                if verify:
                    self.log("Verifying checksum...")
                    success, message = self.verify_checksum_from_metadata(file_path, checksum_path)
                    if success:
                        self.log(message)
                        messagebox.showinfo("Verification Success", message)
                    else:
                        self.log(f"VERIFICATION FAILED: {message}")
                        messagebox.showerror("Verification Failed", 
                            f"{message}\n\nThe database may have been modified or corrupted.\n"
                            "Using this database is not recommended!")
                        
                        # Ask if user wants to continue anyway
                        continue_anyway = messagebox.askyesno(
                            "Continue Anyway?",
                            "Do you want to use this database despite the failed verification?"
                        )
                        
                        if not continue_anyway:
                            return
            else:
                # No checksum file found automatically, ask if user wants to select one manually
                select_checksum = messagebox.askyesno(
                    "No Checksum Found",
                    "No checksum metadata file was found automatically.\n\n"
                    "Would you like to select a checksum file manually for verification?"
                )
                
                if select_checksum:
                    # Let user select checksum file
                    checksum_dir = os.path.dirname(file_path)
                    manual_checksum_path = filedialog.askopenfilename(
                        title="Select Checksum Metadata File",
                        initialdir=checksum_dir,
                        filetypes=[("Checksum files", "*.sha256.json"), ("JSON files", "*.json"), ("All files", "*.*")]
                    )
                    
                    if manual_checksum_path:
                        self.log(f"Verifying with selected checksum file: {os.path.basename(manual_checksum_path)}")
                        success, message = self.verify_checksum_from_metadata(file_path, manual_checksum_path)
                        if success:
                            self.log(message)
                            messagebox.showinfo("Verification Success", message)
                        else:
                            self.log(f"VERIFICATION FAILED: {message}")
                            messagebox.showerror("Verification Failed", 
                                f"{message}\n\nThe database may have been modified or corrupted.\n"
                                "Using this database is not recommended!")
                            
                            # Ask if user wants to continue anyway
                            continue_anyway = messagebox.askyesno(
                                "Continue Anyway?",
                                "Do you want to use this database despite the failed verification?"
                            )
                            
                            if not continue_anyway:
                                return
            
            self.main_db_path = file_path
            self.main_db_var.set(file_path)
            
            # Check if database is encrypted and prompt for passphrase
            if ENCRYPTION_SUPPORT:
                db_enc = DatabaseEncryption(file_path)
                if db_enc.is_database_encrypted():
                    self.log("🔒 Main database is encrypted")
                    
                    passphrase = simpledialog.askstring(
                        "Database Encrypted",
                        f"Main database is encrypted:\n{os.path.basename(file_path)}\n\nPlease enter passphrase:",
                        show='*'
                    )
                    
                    if not passphrase:
                        self.log("❌ No passphrase provided")
                        messagebox.showerror("Error", "Cannot use encrypted database without passphrase!")
                        self.main_db_path = None
                        self.main_db_var.set("")
                        return
                    
                    # Verify passphrase
                    if db_enc.connect_encrypted(passphrase):
                        self.main_db_passphrase = passphrase
                        db_enc.close()
                        self.log("✓ Passphrase verified for main database")
                    else:
                        self.log("❌ Wrong passphrase")
                        messagebox.showerror("Error", "Wrong passphrase!")
                        self.main_db_path = None
                        self.main_db_var.set("")
                        self.main_db_passphrase = None
                        return
            
            self.log(f"Main DB selected: {os.path.basename(file_path)}")
    
    def add_source_db(self):
        """Adds a Source Database"""
        initial_dir = os.path.dirname(os.path.abspath(__file__))
        file_paths = filedialog.askopenfilenames(
            title="Select Source Database(s)",
            initialdir=initial_dir,
            filetypes=[("Database files", "*.db"), ("All files", "*.*")]
        )

        for file_path in file_paths:
            if file_path == self.main_db_path:
                messagebox.showwarning("Warning", "The Main DB cannot be used as a Source DB!")
                continue
                
            if file_path in self.source_db_paths:
                continue
            
            # Check if checksum metadata file exists in same directory
            checksum_path = file_path + ".sha256.json"
            if os.path.exists(checksum_path):
                # Ask user if they want to verify with the found checksum file
                verify = messagebox.askyesno(
                    "Checksum Verification Available",
                    f"A checksum metadata file was found for:\n{os.path.basename(file_path)}\n\n"
                    "Would you like to verify the database integrity?"
                )
                
                if verify:
                    self.log(f"Verifying checksum for {os.path.basename(file_path)}...")
                    success, message = self.verify_checksum_from_metadata(file_path, checksum_path)
                    if success:
                        self.log(message)
                    else:
                        self.log(f"VERIFICATION FAILED: {message}")
                        messagebox.showerror("Verification Failed", 
                            f"{message}\n\nFile: {os.path.basename(file_path)}\n\n"
                            "The database may have been modified or corrupted.\n"
                            "Using this database is not recommended!")
                        
                        # Ask if user wants to continue anyway
                        continue_anyway = messagebox.askyesno(
                            "Continue Anyway?",
                            "Do you want to add this database despite the failed verification?"
                        )
                        
                        if not continue_anyway:
                            continue
            else:
                # No checksum file found automatically, ask if user wants to select one manually
                select_checksum = messagebox.askyesno(
                    "No Checksum Found",
                    f"No checksum metadata file was found for:\n{os.path.basename(file_path)}\n\n"
                    "Would you like to select a checksum file manually for verification?"
                )
                
                if select_checksum:
                    # Let user select checksum file
                    checksum_dir = os.path.dirname(file_path)
                    manual_checksum_path = filedialog.askopenfilename(
                        title=f"Select Checksum Metadata File for {os.path.basename(file_path)}",
                        initialdir=checksum_dir,
                        filetypes=[("Checksum files", "*.sha256.json"), ("JSON files", "*.json"), ("All files", "*.*")]
                    )
                    
                    if manual_checksum_path:
                        self.log(f"Verifying {os.path.basename(file_path)} with selected checksum file...")
                        success, message = self.verify_checksum_from_metadata(file_path, manual_checksum_path)
                        if success:
                            self.log(message)
                        else:
                            self.log(f"VERIFICATION FAILED: {message}")
                            messagebox.showerror("Verification Failed", 
                                f"{message}\n\nFile: {os.path.basename(file_path)}\n\n"
                                "The database may have been modified or corrupted.\n"
                                "Using this database is not recommended!")
                            
                            # Ask if user wants to continue anyway
                            continue_anyway = messagebox.askyesno(
                                "Continue Anyway?",
                                "Do you want to add this database despite the failed verification?"
                            )
                            
                            if not continue_anyway:
                                continue
            
            # Check if database is encrypted and prompt for passphrase
            source_passphrase = None
            if ENCRYPTION_SUPPORT:
                db_enc = DatabaseEncryption(file_path)
                if db_enc.is_database_encrypted():
                    self.log(f"🔒 Source database is encrypted: {os.path.basename(file_path)}")
                    
                    source_passphrase = simpledialog.askstring(
                        "Database Encrypted",
                        f"Source database is encrypted:\n{os.path.basename(file_path)}\n\nPlease enter passphrase:",
                        show='*'
                    )
                    
                    if not source_passphrase:
                        self.log(f"❌ No passphrase provided for {os.path.basename(file_path)}")
                        messagebox.showerror("Error", "Cannot use encrypted database without passphrase!")
                        continue
                    
                    # Verify passphrase
                    if db_enc.connect_encrypted(source_passphrase):
                        db_enc.close()
                        self.log(f"✓ Passphrase verified for {os.path.basename(file_path)}")
                    else:
                        self.log(f"❌ Wrong passphrase for {os.path.basename(file_path)}")
                        messagebox.showerror("Error", f"Wrong passphrase for {os.path.basename(file_path)}!")
                        continue
            
            self.source_db_paths.append(file_path)
            self.source_db_passphrases[file_path] = source_passphrase  # Store passphrase (None if unencrypted)
            self.source_listbox.insert(tk.END, file_path)
            self.log(f"Source DB added: {os.path.basename(file_path)}")
    
    def remove_source_db(self):
        """Removes selected Source Database"""
        selection = self.source_listbox.curselection()
        if selection:
            index = selection[0]
            removed_path = self.source_db_paths.pop(index)
            # Also remove passphrase if exists
            if removed_path in self.source_db_passphrases:
                del self.source_db_passphrases[removed_path]
            self.source_listbox.delete(index)
            self.log(f"Source DB removed: {os.path.basename(removed_path)}")
    
    def clear_source_dbs(self):
        """Removes all Source Databases"""
        self.source_db_paths.clear()
        self.source_db_passphrases.clear()  # Also clear all passphrases
        self.source_listbox.delete(0, tk.END)
        self.log("All Source DBs removed")
    
    def calculate_sha256_checksum(self, file_path):
        """Calculates SHA-256 checksum for a file"""
        sha256_hash = hashlib.sha256()
        try:
            with open(file_path, "rb") as f:
                # Read file in chunks to handle large files
                for byte_block in iter(lambda: f.read(4096), b""):
                    sha256_hash.update(byte_block)
            return sha256_hash.hexdigest()
        except Exception as e:
            self.log(f"Error calculating checksum: {e}")
            return None

    def create_checksum_metadata(self, file_path, checksum):
        """Creates a checksum metadata JSON file"""
        try:
            file_size = os.path.getsize(file_path)
            filename = os.path.basename(file_path)
            
            metadata = {
                "version": "1.0",
                "algorithm": "SHA-256",
                "filename": filename,
                "checksum": checksum,
                "fileSize": file_size,
                "timestamp": int(os.path.getmtime(file_path) * 1000),
                "exportedBy": "WiFi GeoGrabber Python Database Combiner"
            }
            
            return metadata
        except Exception as e:
            self.log(f"Error creating metadata: {e}")
            return None

    def save_checksum_metadata(self, db_path, metadata):
        """Saves checksum metadata to a .sha256.json file"""
        try:
            checksum_path = db_path + ".sha256.json"
            with open(checksum_path, 'w') as f:
                json.dump(metadata, f, indent=2)
            return checksum_path
        except Exception as e:
            self.log(f"Error saving metadata: {e}")
            return None

    def verify_checksum_from_metadata(self, db_path, metadata_path):
        """Verifies database file using checksum metadata"""
        try:
            # Read metadata
            with open(metadata_path, 'r') as f:
                metadata = json.load(f)
            
            # Verify algorithm
            if metadata.get("algorithm") != "SHA-256":
                return False, f"Unsupported algorithm: {metadata.get('algorithm')}"
            
            # Verify file size
            actual_size = os.path.getsize(db_path)
            expected_size = metadata.get("fileSize")
            if actual_size != expected_size:
                return False, f"File size mismatch: expected {expected_size} bytes, got {actual_size} bytes"
            
            # Calculate and verify checksum
            actual_checksum = self.calculate_sha256_checksum(db_path)
            expected_checksum = metadata.get("checksum")
            
            if actual_checksum != expected_checksum:
                return False, "Checksum verification failed! File may have been modified or corrupted."
            
            return True, "Checksum verification successful!"
            
        except Exception as e:
            return False, f"Error during verification: {str(e)}"
    
    def get_table_info(self, db_path, passphrase=None):
        """Gets information about the tables in the database (supports encryption)"""
        try:
            conn = self.open_database_connection(db_path, passphrase)
            if not conn:
                self.log(f"Failed to open database: {os.path.basename(db_path)}")
                return None
            
            cursor = conn.cursor()

            # Check available tables
            cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
            tables = [row[0] for row in cursor.fetchall()]

            info = {
                'has_device_data': 'device_data' in tables,
                'has_wifi_data': 'wifi_data' in tables,
                'tables': tables
            }

            # Count entries
            if info['has_device_data']:
                cursor.execute("SELECT COUNT(*) FROM device_data WHERE latitude != 0 AND longitude != 0 AND device_type = 'BLUETOOTH'")
                info['device_count'] = cursor.fetchone()[0]
            else:
                info['device_count'] = 0

            if info['has_wifi_data']:
                cursor.execute("SELECT COUNT(*) FROM wifi_data WHERE latitude != 0 AND longitude != 0")
                info['wifi_count'] = cursor.fetchone()[0]
            else:
                info['wifi_count'] = 0

            conn.close()
            return info

        except Exception as e:
            self.log(f"Error analyzing {os.path.basename(db_path)}: {str(e)}")
            return None
    
    def analyze_databases(self):
        """Analyzes all selected databases"""
        if not self.main_db_path:
            messagebox.showerror("Error", "Please select a Main Database first!")
            return

        if not self.source_db_paths:
            # Only analyze Main DB
            self.log("=== ANALYSIS STARTED (Main DB only) ===")
            main_info = self.get_table_info(self.main_db_path, self.main_db_passphrase)
            if main_info:
                self.log(f"Main DB: {main_info['device_count']} Device entries, {main_info['wifi_count']} WiFi entries")
                self.log(f"Tables: {', '.join(main_info['tables'])}")
            self.log("=== ANALYSIS COMPLETED ===")
            self.status_var.set("Analysis completed - Ready for cleaning")
            return

        self.log("=== ANALYSIS STARTED ===")

        # Analyze Main DB
        self.log("Analyzing Main Database...")
        main_info = self.get_table_info(self.main_db_path, self.main_db_passphrase)
        if main_info:
            self.log(f"Main DB: {main_info['device_count']} Device entries, {main_info['wifi_count']} WiFi entries")
            self.log(f"Tables: {', '.join(main_info['tables'])}")

        # Analyze Source DBs
        total_source_devices = 0
        total_source_wifi = 0

        for i, source_path in enumerate(self.source_db_paths):
            self.log(f"Analyzing Source DB {i+1}/{len(self.source_db_paths)}...")
            source_passphrase = self.source_db_passphrases.get(source_path)
            source_info = self.get_table_info(source_path, source_passphrase)
            if source_info:
                self.log(f"{os.path.basename(source_path)}: {source_info['device_count']} Device, {source_info['wifi_count']} WiFi")
                total_source_devices += source_info['device_count']
                total_source_wifi += source_info['wifi_count']

        self.log(f"=== ANALYSIS COMPLETED ===")
        self.log(f"Total to process: {total_source_devices} Device entries, {total_source_wifi} WiFi entries")
        self.status_var.set("Analysis completed - Ready for merge")
    
    def load_devices_from_db(self, db_path, passphrase=None):
        """Loads all devices from a database (supports encryption)"""
        devices = []
        try:
            conn = self.open_database_connection(db_path, passphrase)
            if not conn:
                self.log(f"Failed to open database: {os.path.basename(db_path)}")
                return []
            
            cursor = conn.cursor()

            # Load from device_data table - ONLY BLUETOOTH, since WiFi is in wifi_data
            cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='device_data'")
            if cursor.fetchone():
                # Check available columns
                cursor.execute("PRAGMA table_info(device_data)")
                device_columns = [col[1] for col in cursor.fetchall()]
                has_new_fields = 'capabilities' in device_columns
                has_movement = 'last_seen_latitude' in device_columns
                
                if has_new_fields and has_movement:
                    cursor.execute("""
                        SELECT device_name, device_address, device_type, signal_strength, 
                               encryption_info, latitude, longitude, timestamp, frequency, 
                               channel, wifi_standard, vendor_info, channel_width, max_connection_speed,
                               capabilities, center_freq0, center_freq1, 
                               is_passpoint_network, operator_friendly_name, venue_name,
                               last_seen_latitude, last_seen_longitude, last_seen_timestamp, movement_distance
                        FROM device_data 
                        WHERE latitude != 0 AND longitude != 0 AND device_type = 'BLUETOOTH'
                    """)
                elif has_movement:
                    cursor.execute("""
                        SELECT device_name, device_address, device_type, signal_strength, 
                               encryption_info, latitude, longitude, timestamp, frequency, 
                               channel, wifi_standard, vendor_info, channel_width, max_connection_speed,
                               last_seen_latitude, last_seen_longitude, last_seen_timestamp, movement_distance
                        FROM device_data 
                        WHERE latitude != 0 AND longitude != 0 AND device_type = 'BLUETOOTH'
                    """)
                else:
                    cursor.execute("""
                        SELECT device_name, device_address, device_type, signal_strength, 
                               encryption_info, latitude, longitude, timestamp, frequency, 
                               channel, wifi_standard, vendor_info, channel_width, max_connection_speed
                        FROM device_data 
                        WHERE latitude != 0 AND longitude != 0 AND device_type = 'BLUETOOTH'
                    """)
                
                for row in cursor.fetchall():
                    # Handle different number of columns
                    if has_new_fields and has_movement and len(row) >= 24:
                        device_name, device_address, device_type, signal_strength, encryption_info, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed, capabilities, center_freq0, center_freq1, is_passpoint_network, operator_friendly_name, venue_name, last_seen_lat, last_seen_lon, last_seen_timestamp, movement_distance = row
                    elif has_movement and len(row) >= 18:
                        device_name, device_address, device_type, signal_strength, encryption_info, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed, last_seen_lat, last_seen_lon, last_seen_timestamp, movement_distance = row
                        capabilities = center_freq0 = center_freq1 = is_passpoint_network = operator_friendly_name = venue_name = None
                    elif len(row) >= 14:
                        device_name, device_address, device_type, signal_strength, encryption_info, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed = row
                        capabilities = center_freq0 = center_freq1 = is_passpoint_network = operator_friendly_name = venue_name = None
                        last_seen_lat = last_seen_lon = last_seen_timestamp = movement_distance = None
                    else:
                        device_name, device_address, device_type, signal_strength, encryption_info, lat, lon, timestamp = row[:8]
                        frequency = channel = wifi_standard = vendor_info = channel_width = max_speed = None
                        capabilities = center_freq0 = center_freq1 = is_passpoint_network = operator_friendly_name = venue_name = None
                        last_seen_lat = last_seen_lon = last_seen_timestamp = movement_distance = None
                    
                    devices.append({
                        'source': 'device_data',
                        'name': device_name,
                        'address': device_address,
                        'type': device_type,
                        'signal': signal_strength,
                        'encryption': encryption_info,
                        'lat': lat,
                        'lon': lon,
                        'timestamp': timestamp,
                        'frequency': frequency,
                        'channel': channel,
                        'standard': wifi_standard,
                        'vendor': vendor_info,
                        'channel_width': channel_width,
                        'max_speed': max_speed,
                        'capabilities': capabilities,
                        'center_freq0': center_freq0,
                        'center_freq1': center_freq1,
                        'is_passpoint': is_passpoint_network,
                        'operator_name': operator_friendly_name,
                        'venue_name': venue_name,
                        'last_seen_lat': last_seen_lat,
                        'last_seen_lon': last_seen_lon,
                        'last_seen_timestamp': last_seen_timestamp,
                        'movement_distance': movement_distance,
                        'db_source': os.path.basename(db_path)
                    })

            # Load from wifi_data table
            cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='wifi_data'")
            if cursor.fetchone():
                # Check available columns
                cursor.execute("PRAGMA table_info(wifi_data)")
                columns = [column[1] for column in cursor.fetchall()]
                has_extended = 'frequency' in columns
                has_new_fields = 'capabilities' in columns
                
                if has_extended and has_new_fields:
                    cursor.execute("""
                        SELECT ssid, bssid, signal_strength, encryption, 
                               latitude, longitude, timestamp, frequency, channel, 
                               wifi_standard, vendor_info, channel_width, max_connection_speed,
                               capabilities, center_freq0, center_freq1
                        FROM wifi_data 
                        WHERE latitude != 0 AND longitude != 0
                    """)
                elif has_extended:
                    cursor.execute("""
                        SELECT ssid, bssid, signal_strength, encryption, 
                               latitude, longitude, timestamp, frequency, channel, 
                               wifi_standard, vendor_info, channel_width, max_connection_speed
                        FROM wifi_data 
                        WHERE latitude != 0 AND longitude != 0
                    """)
                else:
                    cursor.execute("""
                        SELECT ssid, bssid, signal_strength, encryption, 
                               latitude, longitude, timestamp
                        FROM wifi_data 
                        WHERE latitude != 0 AND longitude != 0
                    """)
                
                for row in cursor.fetchall():
                    if has_extended and has_new_fields and len(row) >= 16:
                        ssid, bssid, signal_strength, encryption, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed, capabilities, center_freq0, center_freq1 = row[:16]
                    elif has_extended and len(row) >= 13:
                        ssid, bssid, signal_strength, encryption, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed = row[:13]
                        capabilities = center_freq0 = center_freq1 = None
                    else:
                        ssid, bssid, signal_strength, encryption, lat, lon, timestamp = row[:7]
                        frequency = channel = wifi_standard = vendor_info = channel_width = max_speed = None
                        capabilities = center_freq0 = center_freq1 = None
                    
                    devices.append({
                        'source': 'wifi_data',
                        'name': ssid,
                        'address': bssid,
                        'type': 'WIFI',
                        'signal': signal_strength,
                        'encryption': encryption,
                        'lat': lat,
                        'lon': lon,
                        'timestamp': timestamp,
                        'frequency': frequency,
                        'channel': channel,
                        'standard': wifi_standard,
                        'vendor': vendor_info,
                        'channel_width': channel_width,
                        'max_speed': max_speed,
                        'capabilities': capabilities,
                        'center_freq0': center_freq0,
                        'center_freq1': center_freq1,
                        'db_source': os.path.basename(db_path)
                    })
            
            conn.close()
            return devices

        except Exception as e:
            self.log(f"Error loading from {os.path.basename(db_path)}: {str(e)}")
            return []
    
    def insert_or_update_device(self, cursor, device, existing_devices):
        """Adds a device or updates it with movement tracking"""
        device_key = f"{device['address']}_{device['type']}"

        if device_key in existing_devices:
            existing = existing_devices[device_key]

            # Calculate distance between old and new position
            old_lat = existing.get('lat', 0)
            old_lon = existing.get('lon', 0)
            new_lat = device.get('lat', 0)
            new_lon = device.get('lon', 0)

            if old_lat and old_lon and new_lat and new_lon:
                distance = self.calculate_distance(old_lat, old_lon, new_lat, new_lon)

                # If distance < 100m, ignore the new entry
                if distance < 100:
                    return 'skipped_too_close'

                # Compare signal strength
                current_signal = existing.get('signal', -999)
                new_signal = device.get('signal', -999)
                current_timestamp = existing.get('timestamp', 0)
                new_timestamp = device.get('timestamp', 0)

                # Decision based on signal and time
                should_update_primary = False
                if new_signal > current_signal:
                    should_update_primary = True
                elif new_signal == current_signal and new_timestamp > current_timestamp:
                    should_update_primary = True
                
                if should_update_primary:
                    # Update main position and save old as Last Seen
                    if device['source'] == 'device_data':
                        cursor.execute("""
                            UPDATE device_data
                            SET signal_strength = ?, latitude = ?, longitude = ?, timestamp = ?,
                                last_seen_latitude = ?, last_seen_longitude = ?, last_seen_timestamp = ?,
                                movement_distance = ?
                            WHERE device_address = ? AND device_type = ?
                        """, (new_signal, new_lat, new_lon, new_timestamp,
                              old_lat, old_lon, current_timestamp, distance,
                              device['address'], device['type']))
                    elif device['source'] == 'wifi_data':
                        cursor.execute("""
                            UPDATE wifi_data
                            SET signal_strength = ?, latitude = ?, longitude = ?, timestamp = ?,
                                last_seen_latitude = ?, last_seen_longitude = ?, last_seen_timestamp = ?,
                                movement_distance = ?
                            WHERE bssid = ?
                        """, (new_signal, new_lat, new_lon, new_timestamp,
                              old_lat, old_lon, current_timestamp, distance,
                              device['address']))

                    self.log(f"Device moved: {device['address']} - {distance:.1f}m away, main position updated")
                    return 'updated_with_movement'
                else:
                    # New position is saved as Last Seen
                    if device['source'] == 'device_data':
                        cursor.execute("""
                            UPDATE device_data
                            SET last_seen_latitude = ?, last_seen_longitude = ?, last_seen_timestamp = ?,
                                movement_distance = ?
                            WHERE device_address = ? AND device_type = ?
                        """, (new_lat, new_lon, new_timestamp, distance,
                              device['address'], device['type']))
                    elif device['source'] == 'wifi_data':
                        cursor.execute("""
                            UPDATE wifi_data
                            SET last_seen_latitude = ?, last_seen_longitude = ?, last_seen_timestamp = ?,
                                movement_distance = ?
                            WHERE bssid = ?
                        """, (new_lat, new_lon, new_timestamp, distance,
                              device['address']))

                    self.log(f"Device moved: {device['address']} - {distance:.1f}m away, Last Seen updated")
                    return 'updated_last_seen'
            else:
                # No valid coordinates for distance calculation
                current_signal = existing.get('signal', -999)
                new_signal = device.get('signal', -999)

                if new_signal > current_signal and self.coordinate_update_var.get():
                    # Only update with better signal
                    if device['source'] == 'device_data':
                        cursor.execute("""
                            UPDATE device_data 
                            SET signal_strength = ?, latitude = ?, longitude = ?, timestamp = ?
                            WHERE device_address = ? AND device_type = ?
                        """, (new_signal, new_lat, new_lon, device['timestamp'],
                              device['address'], device['type']))
                    elif device['source'] == 'wifi_data':
                        cursor.execute("""
                            UPDATE wifi_data 
                            SET signal_strength = ?, latitude = ?, longitude = ?, timestamp = ?
                            WHERE bssid = ?
                        """, (new_signal, new_lat, new_lon, device['timestamp'],
                              device['address']))
                    
                    return 'updated'
                else:
                    return 'skipped_worse_signal'
        else:
            # New device - add it
            if device['source'] == 'device_data':
                cursor.execute("""
                    INSERT INTO device_data 
                    (device_name, device_address, device_type, signal_strength, encryption_info,
                     latitude, longitude, timestamp, frequency, channel, wifi_standard, 
                     vendor_info, channel_width, max_connection_speed,
                     capabilities, center_freq0, center_freq1, 
                     is_passpoint_network, operator_friendly_name, venue_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (device['name'], device['address'], device['type'], device['signal'],
                      device['encryption'], device['lat'], device['lon'], device['timestamp'],
                      device['frequency'], device['channel'], device['standard'],
                      device['vendor'], device['channel_width'], device['max_speed'],
                      device.get('capabilities'), device.get('center_freq0'), device.get('center_freq1'),
                      device.get('is_passpoint'), device.get('operator_name'), device.get('venue_name')))
            elif device['source'] == 'wifi_data':
                cursor.execute("""
                    INSERT INTO wifi_data 
                    (ssid, bssid, signal_strength, encryption, latitude, longitude, timestamp,
                     frequency, channel, wifi_standard, vendor_info, channel_width, max_connection_speed,
                     capabilities, center_freq0, center_freq1)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (device['name'], device['address'], device['signal'], device['encryption'],
                      device['lat'], device['lon'], device['timestamp'], device['frequency'],
                      device['channel'], device['standard'], device['vendor'],
                      device['channel_width'], device['max_speed'],
                      device.get('capabilities'), device.get('center_freq0'), device.get('center_freq1')))
            
            return 'added'
    
    def create_backup(self):
        """Creates a backup of the Main Database"""
        if not self.backup_var.get():
            return True

        try:
            backup_name = f"{os.path.splitext(self.main_db_path)[0]}_backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}.db"
            shutil.copy2(self.main_db_path, backup_name)
            self.log(f"Backup created: {os.path.basename(backup_name)}")
            return True
        except Exception as e:
            self.log(f"Error creating backup: {str(e)}")
            return False
    
    def combine_databases(self):
        """Merges all databases"""
        if not self.main_db_path:
            messagebox.showerror("Error", "Please select a Main Database first!")
            return

        if not self.source_db_paths:
            messagebox.showerror("Error", "Please add at least one Source Database or use 'Clean DB' for a single database!")
            return

        # Create backup
        if not self.create_backup():
            if not messagebox.askyesno("Warning", "Backup failed. Continue anyway?"):
                return

        # Ensure all Source DBs have modern tables/columns
        for source_db in self.source_db_paths:
            try:
                conn = sqlite3.connect(source_db)
                cursor = conn.cursor()
                self.create_modern_tables(cursor)
                conn.commit()
                conn.close()
                self.log(f"Table structure in {os.path.basename(source_db)} updated.")
            except Exception as e:
                self.log(f"Error updating table structure in {os.path.basename(source_db)}: {e}")

        self.log("=== MERGE STARTED ===")

        try:
            # Load existing devices from Main DB
            self.log("Loading existing devices from Main DB...")
            existing_devices = {}
            main_devices = self.load_devices_from_db(self.main_db_path)

            for device in main_devices:
                device_key = f"{device['address']}_{device['type']}"
                existing_devices[device_key] = device

            self.log(f"Found: {len(existing_devices)} existing devices")

            # Open Main DB for updates
            conn = sqlite3.connect(self.main_db_path)
            cursor = conn.cursor()

            # Statistics
            stats = {
                'added': 0,
                'updated': 0,
                'updated_with_movement': 0,
                'updated_last_seen': 0,
                'skipped_too_close': 0,
                'skipped_better_exists': 0,
                'skipped_worse_signal': 0,
                'total_processed': 0
            }

            total_sources = len(self.source_db_paths)

            # Process each Source DB
            for i, source_path in enumerate(self.source_db_paths):
                self.log(f"Processing {os.path.basename(source_path)} ({i+1}/{total_sources})...")
                self.progress_var.set((i / total_sources) * 100)

                source_devices = self.load_devices_from_db(source_path)

                for j, device in enumerate(source_devices):
                    result = self.insert_or_update_device(cursor, device, existing_devices)
                    stats[result] += 1
                    stats['total_processed'] += 1

                    # Update existing devices list for new devices
                    if result == 'added':
                        device_key = f"{device['address']}_{device['type']}"
                        existing_devices[device_key] = device

                    # Progress Update
                    if j % 100 == 0:
                        self.status_var.set(f"Processing {os.path.basename(source_path)}: {j}/{len(source_devices)}")
                        self.root.update()

            # Save changes
            conn.commit()

            # After merge: check and remove duplicates
            self.log("Checking and removing duplicates after merge...")
            removed_duplicates = self.remove_duplicates_after_merge(cursor)

            conn.commit()
            conn.close()

            self.progress_var.set(100)
            self.status_var.set("Merge completed!")
            
            # Display results
            self.log("=== MERGE COMPLETED ===")
            self.log(f"Processed: {stats['total_processed']} devices")
            self.log(f"Newly added: {stats['added']}")
            self.log(f"Updated (better signal): {stats['updated']}")
            self.log(f"Movement detected - main position updated: {stats['updated_with_movement']}")
            self.log(f"Movement detected - Last Seen updated: {stats['updated_last_seen']}")
            self.log(f"Skipped (too close < 100m): {stats['skipped_too_close']}")
            self.log(f"Skipped (worse signal): {stats['skipped_worse_signal']}")
            self.log(f"Skipped (better exists): {stats['skipped_better_exists']}")

            if removed_duplicates:
                self.log(f"Duplicates removed: {removed_duplicates['wifi_removed']} WiFi, {removed_duplicates['bluetooth_removed']} Bluetooth")

            # Show movement statistics
            total_movements = stats['updated_with_movement'] + stats['updated_last_seen']
            if total_movements > 0:
                self.log(f"Device movements detected: {total_movements}")

            # Ask user if they want to create/update checksum
            create_checksum = messagebox.askyesno(
                "Create Checksum?",
                "Would you like to create/update a SHA-256 checksum for the merged database?\n\n"
                "This will help verify the database integrity in the future."
            )
            
            if create_checksum:
                self.log("Calculating SHA-256 checksum...")
                self.status_var.set("Creating checksum...")
                checksum = self.calculate_sha256_checksum(self.main_db_path)
                if checksum:
                    metadata = self.create_checksum_metadata(self.main_db_path, checksum)
                    if metadata:
                        saved_path = self.save_checksum_metadata(self.main_db_path, metadata)
                        if saved_path:
                            self.log(f"Checksum saved: {os.path.basename(saved_path)}")
                            messagebox.showinfo("Checksum Created", 
                                f"Checksum metadata saved to:\n{os.path.basename(saved_path)}")

            messagebox.showinfo("Success",
                f"Merge completed!\n\n"
                f"Newly added: {stats['added']}\n"
                f"Updated: {stats['updated']}\n"
                f"Movements detected: {total_movements}\n"
                f"Skipped (too close): {stats['skipped_too_close']}\n"
                f"Skipped (signal): {stats['skipped_worse_signal'] + stats['skipped_better_exists']}\n"
                f"Duplicates removed: {removed_duplicates.get('total_removed', 0) if removed_duplicates else 0}")

        except Exception as e:
            self.log(f"Error during merge: {str(e)}")
            messagebox.showerror("Error", f"Error during merge:\n{str(e)}")
    
    def clean_database(self):
        """Cleans and sorts a database"""
        if not self.main_db_path:
            messagebox.showerror("Error", "Please select a database to clean first!")
            return

        # Confirmation
        if not messagebox.askyesno("Confirmation",
            f"Do you want to clean and sort the database?\n\n"
            f"- WiFi data will be moved to wifi_data\n"
            f"- Bluetooth data will be moved to device_data\n"
            f"- Duplicates will be removed\n"
            f"- A backup will be created\n\n"
            f"File: {os.path.basename(self.main_db_path)}"):
            return

        # Create backup
        if not self.create_backup():
            if not messagebox.askyesno("Warning", "Backup failed. Continue anyway?"):
                return

        self.log("=== DATABASE CLEANING STARTED ===")
        
        try:
            conn = sqlite3.connect(self.main_db_path)
            cursor = conn.cursor()

            # Analyze current structure
            cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
            existing_tables = [row[0] for row in cursor.fetchall()]

            self.log(f"Existing tables: {', '.join(existing_tables)}")

            # Create modern table structure if not present
            self.create_modern_tables(cursor)

            # SIMPLE CLEANING: Only move WiFi from device_data and remove duplicates
            self.log("Moving WiFi data from device_data to wifi_data...")
            self.migrate_wifi_from_device_data_simple(cursor)

            # Remove duplicates in both tables
            self.log("Removing duplicates in wifi_data...")
            wifi_duplicates_removed = self.remove_wifi_duplicates(cursor)

            self.log("Removing duplicates in device_data...")
            bluetooth_duplicates_removed = self.remove_bluetooth_duplicates(cursor)

            # Delete old tables (optional)
            if messagebox.askyesno("Cleanup", "Do you want to delete old/empty tables?"):
                self.cleanup_old_tables(cursor, existing_tables)

            conn.commit()
            conn.close()

            self.log("=== CLEANING COMPLETED ===")
            self.log(f"WiFi duplicates removed: {wifi_duplicates_removed}")
            self.log(f"Bluetooth duplicates removed: {bluetooth_duplicates_removed}")

            # Ask user if they want to create/update checksum
            create_checksum = messagebox.askyesno(
                "Create Checksum?",
                "Would you like to create/update a SHA-256 checksum for the cleaned database?\n\n"
                "This will help verify the database integrity in the future."
            )
            
            if create_checksum:
                self.log("Calculating SHA-256 checksum...")
                checksum = self.calculate_sha256_checksum(self.main_db_path)
                if checksum:
                    metadata = self.create_checksum_metadata(self.main_db_path, checksum)
                    if metadata:
                        saved_path = self.save_checksum_metadata(self.main_db_path, metadata)
                        if saved_path:
                            self.log(f"Checksum saved: {os.path.basename(saved_path)}")

            messagebox.showinfo("Success",
                f"Database cleaned!\n\n"
                f"WiFi duplicates removed: {wifi_duplicates_removed}\n"
                f"Bluetooth duplicates removed: {bluetooth_duplicates_removed}\n\n"
                f"All data is now sorted in the correct tables.")

        except Exception as e:
            self.log(f"Error during cleaning: {str(e)}")
            messagebox.showerror("Error", f"Error during cleaning:\n{str(e)}")
    
    def repair_database(self):
        """Repairs and updates the database structure"""
        if not self.main_db_path:
            messagebox.showerror("Error", "Please select a database to repair first!")
            return

        # Confirmation
        if not messagebox.askyesno("Confirmation",
            f"Do you want to repair and update the database structure?\n\n"
            f"- Missing movement tracking columns will be added\n"
            f"- Table structures will be modernized\n"
            f"- A backup will be created\n\n"
            f"File: {os.path.basename(self.main_db_path)}"):
            return

        # Create backup
        if not self.create_backup():
            if not messagebox.askyesno("Warning", "Backup failed. Continue anyway?"):
                return

        self.log("=== DATABASE REPAIR STARTED ===")
        
        try:
            conn = sqlite3.connect(self.main_db_path)
            cursor = conn.cursor()

            # Analyze current structure
            cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
            existing_tables = [row[0] for row in cursor.fetchall()]

            self.log(f"Existing tables: {', '.join(existing_tables)}")

            # Statistics for repair
            repair_stats = {
                'tables_created': 0,
                'columns_added': 0,
                'tables_analyzed': len(existing_tables)
            }

            # Create modern table structure if not present
            self.log("Checking and creating modern table structures...")
            if 'wifi_data' not in existing_tables:
                self.create_modern_tables(cursor)
                repair_stats['tables_created'] += 1
                self.log("Table 'wifi_data' created")

            if 'device_data' not in existing_tables:
                if repair_stats['tables_created'] == 0:  # Only if wifi_data already existed
                    self.create_modern_tables(cursor)
                    repair_stats['tables_created'] += 1
                self.log("Table 'device_data' created")

            # Add movement tracking columns
            self.log("Checking and adding movement tracking columns...")
            columns_before = self.count_movement_columns(cursor)
            self.add_movement_tracking_columns(cursor)
            columns_after = self.count_movement_columns(cursor)
            repair_stats['columns_added'] = columns_after - columns_before

            # Check all tables for missing standard columns
            self.log("Checking and repairing standard columns...")
            additional_columns = self.repair_standard_columns(cursor, existing_tables)
            repair_stats['columns_added'] += additional_columns

            # Create indexes for better performance
            self.log("Creating performance indexes...")
            self.create_performance_indexes(cursor)

            conn.commit()
            conn.close()

            self.log("=== REPAIR COMPLETED ===")
            self.log(f"Tables created: {repair_stats['tables_created']}")
            self.log(f"Columns added: {repair_stats['columns_added']}")
            self.log(f"Tables analyzed: {repair_stats['tables_analyzed']}")

            # Ask user if they want to create/update checksum
            create_checksum = messagebox.askyesno(
                "Create Checksum?",
                "Would you like to create/update a SHA-256 checksum for the repaired database?\n\n"
                "This will help verify the database integrity in the future."
            )
            
            if create_checksum:
                self.log("Calculating SHA-256 checksum...")
                checksum = self.calculate_sha256_checksum(self.main_db_path)
                if checksum:
                    metadata = self.create_checksum_metadata(self.main_db_path, checksum)
                    if metadata:
                        saved_path = self.save_checksum_metadata(self.main_db_path, metadata)
                        if saved_path:
                            self.log(f"Checksum saved: {os.path.basename(saved_path)}")

            messagebox.showinfo("Success",
                f"Database repair completed!\n\n"
                f"Tables created: {repair_stats['tables_created']}\n"
                f"Columns added: {repair_stats['columns_added']}\n"
                f"Tables analyzed: {repair_stats['tables_analyzed']}\n\n"
                f"The database is now up to date.")

        except Exception as e:
            self.log(f"Error during repair: {str(e)}")
            messagebox.showerror("Error", f"Error during repair:\n{str(e)}")
    
    def count_movement_columns(self, cursor):
        """Counts existing movement tracking columns"""
        count = 0

        # Check wifi_data
        try:
            cursor.execute("PRAGMA table_info(wifi_data)")
            wifi_columns = [col[1] for col in cursor.fetchall()]
            movement_cols = ['last_seen_latitude', 'last_seen_longitude', 'last_seen_timestamp', 'movement_distance']
            count += sum(1 for col in movement_cols if col in wifi_columns)
        except:
            pass

        # Check device_data
        try:
            cursor.execute("PRAGMA table_info(device_data)")
            device_columns = [col[1] for col in cursor.fetchall()]
            movement_cols = ['last_seen_latitude', 'last_seen_longitude', 'last_seen_timestamp', 'movement_distance']
            count += sum(1 for col in movement_cols if col in device_columns)
        except:
            pass

        return count
    
    def repair_standard_columns(self, cursor, existing_tables):
        """Repairs and adds missing standard columns"""
        columns_added = 0

        # Standard columns for wifi_data
        wifi_standard_columns = {
            'frequency': 'INTEGER',
            'channel': 'INTEGER',
            'wifi_standard': 'TEXT',
            'vendor_info': 'TEXT',
            'channel_width': 'TEXT',
            'max_connection_speed': 'INTEGER',
            'capabilities': 'TEXT',
            'center_freq0': 'INTEGER',
            'center_freq1': 'INTEGER'
        }

        # Standard columns for device_data
        device_standard_columns = {
            'frequency': 'INTEGER',
            'channel': 'INTEGER',
            'wifi_standard': 'TEXT',
            'vendor_info': 'TEXT',
            'channel_width': 'TEXT',
            'max_connection_speed': 'INTEGER',
            'capabilities': 'TEXT',
            'center_freq0': 'INTEGER',
            'center_freq1': 'INTEGER',
            'is_passpoint_network': 'INTEGER',
            'operator_friendly_name': 'TEXT',
            'venue_name': 'TEXT'
        }

        # Repair wifi_data
        if 'wifi_data' in existing_tables:
            try:
                cursor.execute("PRAGMA table_info(wifi_data)")
                wifi_columns = [col[1] for col in cursor.fetchall()]

                for col_name, col_type in wifi_standard_columns.items():
                    if col_name not in wifi_columns:
                        try:
                            cursor.execute(f"ALTER TABLE wifi_data ADD COLUMN {col_name} {col_type}")
                            self.log(f"Column '{col_name}' added to wifi_data")
                            columns_added += 1
                        except Exception as e:
                            self.log(f"Warning: Could not add column {col_name} to wifi_data: {str(e)}")
            except Exception as e:
                self.log(f"Error repairing wifi_data: {str(e)}")

        # Repair device_data
        if 'device_data' in existing_tables:
            try:
                cursor.execute("PRAGMA table_info(device_data)")
                device_columns = [col[1] for col in cursor.fetchall()]

                for col_name, col_type in device_standard_columns.items():
                    if col_name not in device_columns:
                        try:
                            cursor.execute(f"ALTER TABLE device_data ADD COLUMN {col_name} {col_type}")
                            self.log(f"Column '{col_name}' added to device_data")
                            columns_added += 1
                        except Exception as e:
                            self.log(f"Warning: Could not add column {col_name} to device_data: {str(e)}")
            except Exception as e:
                self.log(f"Error repairing device_data: {str(e)}")

        return columns_added
    
    def create_performance_indexes(self, cursor):
        """Creates indexes for better performance"""
        indexes = [
            # WiFi data indexes
            ("idx_wifi_bssid", "wifi_data", "bssid"),
            ("idx_wifi_signal", "wifi_data", "signal_strength"),
            ("idx_wifi_coords", "wifi_data", "latitude, longitude"),
            ("idx_wifi_timestamp", "wifi_data", "timestamp"),

            # Device data indexes
            ("idx_device_address", "device_data", "device_address"),
            ("idx_device_type", "device_data", "device_type"),
            ("idx_device_signal", "device_data", "signal_strength"),
            ("idx_device_coords", "device_data", "latitude, longitude"),
            ("idx_device_timestamp", "device_data", "timestamp"),
        ]

        for index_name, table_name, columns in indexes:
            try:
                cursor.execute(f"CREATE INDEX IF NOT EXISTS {index_name} ON {table_name} ({columns})")
                self.log(f"Index '{index_name}' created/verified")
            except Exception as e:
                self.log(f"Warning: Could not create index {index_name}: {str(e)}")
    
    def create_modern_tables(self, cursor):
        """Creates modern table structures with movement tracking"""
        # WiFi data table with Last Seen coordinates
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS wifi_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ssid TEXT,
                bssid TEXT NOT NULL,
                signal_strength INTEGER,
                encryption TEXT,
                frequency INTEGER,
                channel INTEGER,
                capabilities TEXT,
                wifi_standard TEXT,
                vendor_info TEXT,
                channel_width TEXT,
                center_freq0 INTEGER,
                center_freq1 INTEGER,
                max_connection_speed INTEGER,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                timestamp INTEGER,
                UNIQUE(bssid)
            )
        """)

        # Device data table with Last Seen coordinates
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS device_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_name TEXT,
                device_address TEXT NOT NULL,
                device_type TEXT NOT NULL,
                signal_strength INTEGER,
                encryption_info TEXT,
                frequency INTEGER,
                channel INTEGER,
                channel_width TEXT,
                capabilities TEXT,
                center_freq0 INTEGER,
                center_freq1 INTEGER,
                wifi_standard TEXT,
                vendor_info TEXT,
                is_passpoint_network INTEGER,
                operator_friendly_name TEXT,
                venue_name TEXT,
                max_connection_speed INTEGER,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                timestamp INTEGER,
                last_seen_latitude REAL,
                last_seen_longitude REAL,
                last_seen_timestamp INTEGER,
                movement_distance REAL,
                UNIQUE(device_address, device_type)
            )
        """)

        # Add new columns to existing tables if they don't exist
        self.add_movement_tracking_columns(cursor)
    
    def add_movement_tracking_columns(self, cursor):
        """Adds movement tracking columns to existing tables"""
        try:
            # Check wifi_data table
            cursor.execute("PRAGMA table_info(wifi_data)")
            wifi_columns = [col[1] for col in cursor.fetchall()]

            # New columns for wifi_data
            wifi_new_columns = {
                'capabilities': 'TEXT',
                'center_freq0': 'INTEGER',
                'center_freq1': 'INTEGER'
            }

            for col_name, col_type in wifi_new_columns.items():
                if col_name not in wifi_columns:
                    cursor.execute(f"ALTER TABLE wifi_data ADD COLUMN {col_name} {col_type}")
                    self.log(f"Column '{col_name}' added to wifi_data")

            # Check device_data table
            cursor.execute("PRAGMA table_info(device_data)")
            device_columns = [col[1] for col in cursor.fetchall()]

            # New columns for device_data
            device_new_columns = {
                'capabilities': 'TEXT',
                'center_freq0': 'INTEGER',
                'center_freq1': 'INTEGER',
                'is_passpoint_network': 'INTEGER',
                'operator_friendly_name': 'TEXT',
                'venue_name': 'TEXT',
                'last_seen_latitude': 'REAL',
                'last_seen_longitude': 'REAL',
                'last_seen_timestamp': 'INTEGER',
                'movement_distance': 'REAL'
            }

            for col_name, col_type in device_new_columns.items():
                if col_name not in device_columns:
                    cursor.execute(f"ALTER TABLE device_data ADD COLUMN {col_name} {col_type}")
                    self.log(f"Column '{col_name}' added to device_data")

        except Exception as e:
            self.log(f"Error adding movement tracking columns: {str(e)}")
    
    def calculate_distance(self, lat1, lon1, lat2, lon2):
        """Calculates the distance between two GPS coordinates in meters (Haversine formula)"""
        import math

        # Earth radius in kilometers
        R = 6371.0

        # Convert coordinates to radians
        lat1_rad = math.radians(lat1)
        lon1_rad = math.radians(lon1)
        lat2_rad = math.radians(lat2)
        lon2_rad = math.radians(lon2)

        # Calculate differences
        dlat = lat2_rad - lat1_rad
        dlon = lon2_rad - lon1_rad

        # Haversine formula
        a = math.sin(dlat/2)**2 + math.cos(lat1_rad) * math.cos(lat2_rad) * math.sin(dlon/2)**2
        c = 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))
        distance_km = R * c

        # Convert to meters
        return distance_km * 1000
    
    def extract_data_from_device_table(self, cursor):
        """Extracts data from device_data table"""
        data = []
        try:
            cursor.execute("PRAGMA table_info(device_data)")
            columns = [col[1] for col in cursor.fetchall()]
            
            if len(columns) >= 8:
                cursor.execute("""
                    SELECT device_name, device_address, device_type, signal_strength,
                           encryption_info, latitude, longitude, timestamp,
                           COALESCE(frequency, NULL), COALESCE(channel, NULL),
                           COALESCE(wifi_standard, NULL), COALESCE(vendor_info, NULL),
                           COALESCE(channel_width, NULL), COALESCE(max_connection_speed, NULL)
                    FROM device_data 
                    WHERE latitude != 0 AND longitude != 0
                """)
                
                for row in cursor.fetchall():
                    data.append({
                        'name': row[0],
                        'address': row[1],
                        'type': row[2],
                        'signal': row[3],
                        'encryption': row[4],
                        'lat': row[5],
                        'lon': row[6],
                        'timestamp': row[7],
                        'frequency': row[8],
                        'channel': row[9],
                        'standard': row[10],
                        'vendor': row[11],
                        'channel_width': row[12],
                        'max_speed': row[13],
                        'source_table': 'device_data'
                    })
        except Exception as e:
            self.log(f"Error extracting from device_data: {str(e)}")

        return data

    def extract_data_from_wifi_table(self, cursor):
        """Extracts data from wifi_data table"""
        data = []
        try:
            cursor.execute("PRAGMA table_info(wifi_data)")
            columns = [col[1] for col in cursor.fetchall()]
            
            if len(columns) >= 7:
                cursor.execute("""
                    SELECT ssid, bssid, signal_strength, encryption,
                           latitude, longitude, timestamp,
                           COALESCE(frequency, NULL), COALESCE(channel, NULL),
                           COALESCE(wifi_standard, NULL), COALESCE(vendor_info, NULL),
                           COALESCE(channel_width, NULL), COALESCE(max_connection_speed, NULL)
                    FROM wifi_data 
                    WHERE latitude != 0 AND longitude != 0
                """)
                
                for row in cursor.fetchall():
                    data.append({
                        'name': row[0],
                        'address': row[1],
                        'type': 'WIFI',
                        'signal': row[2],
                        'encryption': row[3],
                        'lat': row[4],
                        'lon': row[5],
                        'timestamp': row[6],
                        'frequency': row[7],
                        'channel': row[8],
                        'standard': row[9],
                        'vendor': row[10],
                        'channel_width': row[11],
                        'max_speed': row[12],
                        'source_table': 'wifi_data'
                    })
        except Exception as e:
            self.log(f"Error extracting from wifi_data: {str(e)}")

        return data
    
    def extract_data_from_legacy_table(self, cursor, table_name):
        """Extracts data from legacy table formats"""
        data = []
        try:
            cursor.execute(f"PRAGMA table_info({table_name})")
            columns = [col[1] for col in cursor.fetchall()]

            # Determine table type based on columns
            if 'bssid' in columns or 'ssid' in columns:
                # WiFi table
                cursor.execute(f"""
                    SELECT 
                        COALESCE(ssid, device_name, name, '') as name,
                        COALESCE(bssid, mac_address, address, '') as address,
                        signal_strength,
                        COALESCE(encryption, security, '') as encryption,
                        latitude, longitude, timestamp
                    FROM {table_name} 
                    WHERE latitude != 0 AND longitude != 0
                """)

                for row in cursor.fetchall():
                    if row[1]:  # address not empty
                        data.append({
                            'name': row[0],
                            'address': row[1],
                            'type': 'WIFI',
                            'signal': row[2],
                            'encryption': row[3],
                            'lat': row[4],
                            'lon': row[5],
                            'timestamp': row[6],
                            'frequency': None,
                            'channel': None,
                            'standard': None,
                            'vendor': None,
                            'channel_width': None,
                            'max_speed': None,
                            'source_table': table_name
                        })

            elif 'device_type' in columns:
                # Mixed table
                cursor.execute(f"""
                    SELECT device_name, device_address, device_type, signal_strength,
                           COALESCE(encryption_info, '') as encryption,
                           latitude, longitude, timestamp
                    FROM {table_name}
                    WHERE latitude != 0 AND longitude != 0
                """)

                for row in cursor.fetchall():
                    if row[1]:  # address not empty
                        data.append({
                            'name': row[0],
                            'address': row[1],
                            'type': row[2],
                            'signal': row[3],
                            'encryption': row[4],
                            'lat': row[5],
                            'lon': row[6],
                            'timestamp': row[7],
                            'frequency': None,
                            'channel': None,
                            'standard': None,
                            'vendor': None,
                            'channel_width': None,
                            'max_speed': None,
                            'source_table': table_name
                        })

        except Exception as e:
            self.log(f"Error extracting from {table_name}: {str(e)}")

        return data
    
    def clean_and_sort_data(self, all_data):
        """Cleans and sorts data by type"""
        wifi_dict = {}
        bluetooth_dict = {}

        for item in all_data:
            if not item['address']:
                continue

            # Determine type based on various criteria
            device_type = item['type'].upper() if item['type'] else ''

            # WiFi detection
            if (device_type in ['WIFI', 'WI-FI', 'WIRELESS'] or
                (item['name'] and len(item['address']) == 17 and ':' in item['address'])):
                
                key = item['address'].upper()
                
                if key not in wifi_dict or self.is_better_entry(item, wifi_dict[key]):
                    wifi_dict[key] = {
                        'ssid': item['name'] or '',
                        'bssid': key,
                        'signal_strength': item['signal'] or -100,
                        'encryption': item['encryption'] or '',
                        'latitude': item['lat'],
                        'longitude': item['lon'],
                        'timestamp': item['timestamp'] or 0,
                        'frequency': item['frequency'],
                        'channel': item['channel'],
                        'wifi_standard': item['standard'],
                        'vendor_info': item['vendor'],
                        'channel_width': item['channel_width'],
                        'max_connection_speed': item['max_speed']
                    }

            # Bluetooth detection
            elif (device_type in ['BLUETOOTH', 'BT', 'BLE'] or
                  (len(item['address']) == 17 and ':' in item['address'])):
                
                key = f"{item['address'].upper()}_BLUETOOTH"
                
                if key not in bluetooth_dict or self.is_better_entry(item, bluetooth_dict[key]):
                    bluetooth_dict[key] = {
                        'device_name': item['name'] or '',
                        'device_address': item['address'].upper(),
                        'device_type': 'BLUETOOTH',
                        'signal_strength': item['signal'] or -100,
                        'encryption_info': item['encryption'] or '',
                        'latitude': item['lat'],
                        'longitude': item['lon'],
                        'timestamp': item['timestamp'] or 0,
                        'frequency': item['frequency'],
                        'channel': item['channel'],
                        'wifi_standard': item['standard'],
                        'vendor_info': item['vendor'],
                        'channel_width': item['channel_width'],
                        'max_connection_speed': item['max_speed']
                    }

        self.log(f"After cleaning: {len(wifi_dict)} WiFi, {len(bluetooth_dict)} Bluetooth")

        return list(wifi_dict.values()), list(bluetooth_dict.values())
    
    def is_better_entry(self, new_item, existing_item):
        """Checks if a new entry is better than the existing one"""
        new_signal = new_item.get('signal', -999)
        existing_signal = existing_item.get('signal_strength', existing_item.get('signal', -999))

        new_timestamp = new_item.get('timestamp', 0)
        existing_timestamp = existing_item.get('timestamp', 0)

        # Prefer better signal
        if new_signal > existing_signal:
            return True

        # With same signal, prefer newer timestamp
        if new_signal == existing_signal and new_timestamp > existing_timestamp:
            return True

        return False
    
    def insert_cleaned_data(self, cursor, wifi_data, bluetooth_data):
        """Inserts cleaned data into the correct tables"""
        # WiFi data: only add new ones, as migration has already occurred
        for wifi in wifi_data:
            try:
                cursor.execute("""
                    INSERT OR IGNORE INTO wifi_data
                    (ssid, bssid, signal_strength, encryption, latitude, longitude, timestamp,
                     frequency, channel, wifi_standard, vendor_info, channel_width, max_connection_speed,
                     capabilities, center_freq0, center_freq1)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (wifi['ssid'], wifi['bssid'], wifi['signal_strength'], wifi['encryption'],
                      wifi['latitude'], wifi['longitude'], wifi['timestamp'], wifi['frequency'],
                      wifi['channel'], wifi['wifi_standard'], wifi['vendor_info'],
                      wifi['channel_width'], wifi['max_connection_speed'],
                      wifi.get('capabilities'), wifi.get('center_freq0'), wifi.get('center_freq1')))
            except Exception as e:
                self.log(f"Error inserting WiFi {wifi['bssid']}: {str(e)}")

        # Bluetooth data: only add new ones, keep existing
        for bt in bluetooth_data:
            try:
                cursor.execute("""
                    INSERT OR IGNORE INTO device_data
                    (device_name, device_address, device_type, signal_strength, encryption_info,
                     latitude, longitude, timestamp, frequency, channel, wifi_standard,
                     vendor_info, channel_width, max_connection_speed,
                     capabilities, center_freq0, center_freq1, 
                     is_passpoint_network, operator_friendly_name, venue_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (bt['device_name'], bt['device_address'], bt['device_type'], bt['signal_strength'],
                      bt['encryption_info'], bt['latitude'], bt['longitude'], bt['timestamp'],
                      bt['frequency'], bt['channel'], bt['wifi_standard'], bt['vendor_info'],
                      bt['channel_width'], bt['max_connection_speed'],
                      bt.get('capabilities'), bt.get('center_freq0'), bt.get('center_freq1'),
                      bt.get('is_passpoint_network'), bt.get('operator_friendly_name'), bt.get('venue_name')))
            except Exception as e:
                self.log(f"Error inserting Bluetooth {bt['device_address']}: {str(e)}")
    
    def cleanup_old_tables(self, cursor, existing_tables):
        """Deletes old or empty tables"""
        tables_to_check = ['wifi_networks', 'bluetooth_devices', 'scanned_devices']

        for table in tables_to_check:
            if table in existing_tables:
                try:
                    # Check if table is empty
                    cursor.execute(f"SELECT COUNT(*) FROM {table}")
                    count = cursor.fetchone()[0]

                    if count == 0:
                        cursor.execute(f"DROP TABLE {table}")
                        self.log(f"Empty table deleted: {table}")
                    else:
                        self.log(f"Table {table} kept (still contains {count} entries)")

                except Exception as e:
                    self.log(f"Error checking/deleting {table}: {str(e)}")
    
    def migrate_wifi_from_device_data(self, cursor, wifi_data):
        """Migrates WiFi data from device_data to wifi_data and removes it from device_data"""
        try:
            # Create a dictionary of WiFi data by BSSID for quick access
            wifi_dict = {wifi['bssid']: wifi for wifi in wifi_data}

            # Get all WiFi entries from device_data
            cursor.execute("""
                SELECT device_name, device_address, signal_strength, encryption_info,
                       latitude, longitude, timestamp, frequency, channel, wifi_standard,
                       vendor_info, channel_width, max_connection_speed
                FROM device_data
                WHERE device_type = 'WIFI' AND latitude != 0 AND longitude != 0
            """)

            device_wifi_entries = cursor.fetchall()
            self.log(f"Found: {len(device_wifi_entries)} WiFi entries in device_data to migrate")

            migrated_count = 0
            updated_count = 0
            skipped_count = 0

            for row in device_wifi_entries:
                if len(row) >= 13:
                    device_name, device_address, signal_strength, encryption_info, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed = row
                else:
                    device_name, device_address, signal_strength, encryption_info, lat, lon, timestamp = row[:7]
                    frequency = channel = wifi_standard = vendor_info = channel_width = max_speed = None
                
                bssid = device_address.upper()

                # Check if already in wifi_data
                if bssid in wifi_dict:
                    existing_wifi = wifi_dict[bssid]
                    existing_signal = existing_wifi['signal_strength']

                    # Compare signal strength
                    if signal_strength and signal_strength > existing_signal:
                        # Better signal in device_data - update wifi_data
                        cursor.execute("""
                            UPDATE wifi_data
                            SET ssid = ?, signal_strength = ?, encryption = ?,
                                latitude = ?, longitude = ?, timestamp = ?,
                                frequency = ?, channel = ?, wifi_standard = ?,
                                vendor_info = ?, channel_width = ?, max_connection_speed = ?
                            WHERE bssid = ?
                        """, (device_name, signal_strength, encryption_info, lat, lon, timestamp,
                              frequency, channel, wifi_standard, vendor_info, channel_width, max_speed, bssid))
                        updated_count += 1
                        self.log(f"WiFi updated: {device_name} ({bssid}) - better signal")
                    else:
                        # Worse signal - keep existing data
                        skipped_count += 1
                else:
                    # Not in wifi_data - add it
                    cursor.execute("""
                        INSERT INTO wifi_data
                        (ssid, bssid, signal_strength, encryption, latitude, longitude, timestamp,
                         frequency, channel, wifi_standard, vendor_info, channel_width, max_connection_speed)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, (device_name, bssid, signal_strength, encryption_info, lat, lon, timestamp,
                          frequency, channel, wifi_standard, vendor_info, channel_width, max_speed))
                    migrated_count += 1

            # Remove all WiFi entries from device_data
            cursor.execute("DELETE FROM device_data WHERE device_type = 'WIFI'")
            deleted_count = cursor.rowcount

            self.log(f"Migration completed:")
            self.log(f"  - {migrated_count} new WiFi entries added to wifi_data")
            self.log(f"  - {updated_count} WiFi entries updated in wifi_data")
            self.log(f"  - {skipped_count} WiFi entries skipped (worse signal)")
            self.log(f"  - {deleted_count} WiFi entries removed from device_data")

        except Exception as e:
            self.log(f"Error during WiFi migration: {str(e)}")
    
    def migrate_wifi_from_device_data_simple(self, cursor):
        """Simple migration: Moves WiFi data from device_data to wifi_data"""
        try:
            # Check if WiFi entries exist in device_data
            cursor.execute("SELECT COUNT(*) FROM device_data WHERE device_type = 'WIFI'")
            wifi_count = cursor.fetchone()[0]

            if wifi_count == 0:
                self.log("No WiFi entries found in device_data.")
                return

            self.log(f"Found: {wifi_count} WiFi entries in device_data")

            # Move WiFi data to wifi_data
            cursor.execute("""
                INSERT OR IGNORE INTO wifi_data
                (ssid, bssid, signal_strength, encryption, latitude, longitude, timestamp,
                 frequency, channel, wifi_standard, vendor_info, channel_width, max_connection_speed)
                SELECT device_name, device_address, signal_strength, encryption_info, latitude, longitude, timestamp,
                       frequency, channel, wifi_standard, vendor_info, channel_width, max_connection_speed
                FROM device_data
                WHERE device_type = 'WIFI' AND latitude != 0 AND longitude != 0
            """)

            inserted_count = cursor.rowcount

            # Remove WiFi entries from device_data
            cursor.execute("DELETE FROM device_data WHERE device_type = 'WIFI'")
            deleted_count = cursor.rowcount

            self.log(f"Migration completed: {inserted_count} inserted, {deleted_count} removed from device_data")

        except Exception as e:
            self.log(f"Error during simple WiFi migration: {str(e)}")
    
    def remove_wifi_duplicates(self, cursor):
        """Removes WiFi duplicates based on BSSID"""
        try:
            # Find duplicates
            cursor.execute("""
                SELECT bssid, COUNT(*) as count
                FROM wifi_data
                GROUP BY bssid
                HAVING COUNT(*) > 1
            """)
            duplicates = cursor.fetchall()

            if not duplicates:
                self.log("No WiFi duplicates found.")
                return 0

            total_removed = 0
            for bssid, count in duplicates:
                # Keep only the best entry (best signal, newest timestamp)
                cursor.execute("""
                    DELETE FROM wifi_data
                    WHERE bssid = ? AND id NOT IN (
                        SELECT id FROM wifi_data
                        WHERE bssid = ?
                        ORDER BY signal_strength DESC, timestamp DESC, id DESC
                        LIMIT 1
                    )
                """, (bssid, bssid))
                removed = cursor.rowcount
                total_removed += removed
                if removed > 0:
                    self.log(f"WiFi {bssid}: {removed} duplicates removed")

            return total_removed

        except Exception as e:
            self.log(f"Error removing WiFi duplicates: {str(e)}")
            return 0
    
    def remove_bluetooth_duplicates(self, cursor):
        """Removes Bluetooth duplicates based on device_address"""
        try:
            # Find duplicates
            cursor.execute("""
                SELECT device_address, COUNT(*) as count
                FROM device_data
                WHERE device_type = 'BLUETOOTH'
                GROUP BY device_address
                HAVING COUNT(*) > 1
            """)
            duplicates = cursor.fetchall()

            if not duplicates:
                self.log("No Bluetooth duplicates found.")
                return 0

            total_removed = 0
            for device_address, count in duplicates:
                # Keep only the best entry (best signal, newest timestamp)
                cursor.execute("""
                    DELETE FROM device_data
                    WHERE device_address = ? AND device_type = 'BLUETOOTH' AND id NOT IN (
                        SELECT id FROM device_data
                        WHERE device_address = ? AND device_type = 'BLUETOOTH'
                        ORDER BY signal_strength DESC, timestamp DESC, id DESC
                        LIMIT 1
                    )
                """, (device_address, device_address))
                removed = cursor.rowcount
                total_removed += removed
                if removed > 0:
                    self.log(f"Bluetooth {device_address}: {removed} duplicates removed")

            return total_removed

        except Exception as e:
            self.log(f"Error removing Bluetooth duplicates: {str(e)}")
            return 0
    
    def remove_duplicates_after_merge(self, cursor):
        """Removes duplicates after merge based on better signal and newer timestamp"""
        try:
            wifi_removed = 0
            bluetooth_removed = 0

            # 1. Remove WiFi duplicates in wifi_data
            self.log("Removing WiFi duplicates based on BSSID...")
            cursor.execute("""
                DELETE FROM wifi_data 
                WHERE id NOT IN (
                    SELECT MIN(id) 
                    FROM wifi_data 
                    GROUP BY bssid 
                    HAVING COUNT(*) > 1
                    UNION
                    SELECT id 
                    FROM wifi_data 
                    WHERE bssid IN (
                        SELECT bssid 
                        FROM wifi_data 
                        GROUP BY bssid 
                        HAVING COUNT(*) > 1
                    )
                    AND (signal_strength, timestamp, id) IN (
                        SELECT MAX(signal_strength), MAX(timestamp), MAX(id)
                        FROM wifi_data 
                        GROUP BY bssid 
                        HAVING COUNT(*) > 1
                    )
                    UNION
                    SELECT id 
                    FROM wifi_data 
                    WHERE bssid NOT IN (
                        SELECT bssid 
                        FROM wifi_data 
                        GROUP BY bssid 
                        HAVING COUNT(*) > 1
                    )
                )
            """)
            wifi_removed = cursor.rowcount

            # Simpler method for WiFi duplicates
            if wifi_removed == 0:
                # Find and remove WiFi duplicates with better logic
                cursor.execute("""
                    SELECT bssid, COUNT(*) as count
                    FROM wifi_data
                    GROUP BY bssid
                    HAVING COUNT(*) > 1
                """)
                wifi_duplicates = cursor.fetchall()

                for bssid, count in wifi_duplicates:
                    # Keep the entry with best signal and newest timestamp
                    cursor.execute("""
                        DELETE FROM wifi_data
                        WHERE bssid = ? AND id NOT IN (
                            SELECT id FROM wifi_data
                            WHERE bssid = ?
                            ORDER BY signal_strength DESC, timestamp DESC, id DESC
                            LIMIT 1
                        )
                    """, (bssid, bssid))
                    wifi_removed += cursor.rowcount

            # 2. Remove Bluetooth duplicates in device_data
            self.log("Removing Bluetooth duplicates based on device_address...")
            cursor.execute("""
                SELECT device_address, COUNT(*) as count
                FROM device_data
                WHERE device_type = 'BLUETOOTH'
                GROUP BY device_address
                HAVING COUNT(*) > 1
            """)
            bluetooth_duplicates = cursor.fetchall()

            for device_address, count in bluetooth_duplicates:
                # Keep the entry with best signal and newest timestamp
                cursor.execute("""
                    DELETE FROM device_data
                    WHERE device_address = ? AND device_type = 'BLUETOOTH' AND id NOT IN (
                        SELECT id FROM device_data
                        WHERE device_address = ? AND device_type = 'BLUETOOTH'
                        ORDER BY signal_strength DESC, timestamp DESC, id DESC
                        LIMIT 1
                    )
                """, (device_address, device_address))
                bluetooth_removed += cursor.rowcount

            total_removed = wifi_removed + bluetooth_removed

            if total_removed > 0:
                self.log(f"Duplicates removed: {wifi_removed} WiFi, {bluetooth_removed} Bluetooth (Total: {total_removed})")
            else:
                self.log("No duplicates found.")

            return {
                'wifi_removed': wifi_removed,
                'bluetooth_removed': bluetooth_removed,
                'total_removed': total_removed
            }

        except Exception as e:
            self.log(f"Error removing duplicates: {str(e)}")
            return None
    
    def run(self):
        """Starts the GUI"""
        self.root.mainloop()

def main():
    """Main function"""
    app = DatabaseCombiner()
    app.run()

if __name__ == "__main__":
    main()
