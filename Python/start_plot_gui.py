import sqlite3
import folium
from folium import plugins
import tkinter as tk
from tkinter import filedialog, messagebox, simpledialog
import os
import sys
import webbrowser
import tempfile
import json
import hashlib

# Add scripts directory to path for imports
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'scripts'))

# Import encryption helper
try:
    from database_encryption import DatabaseEncryption, SQLCIPHER_AVAILABLE
    ENCRYPTION_SUPPORT = True
except ImportError:
    ENCRYPTION_SUPPORT = False
    print("Warning: Database encryption module not found. Encrypted databases cannot be opened.")


def calculate_sha256_checksum(file_path):
    """Calculates SHA-256 checksum for a file"""
    sha256_hash = hashlib.sha256()
    try:
        with open(file_path, "rb") as f:
            # Read file in chunks to handle large files
            for byte_block in iter(lambda: f.read(4096), b""):
                sha256_hash.update(byte_block)
        return sha256_hash.hexdigest()
    except Exception as e:
        print(f"Error calculating checksum: {e}")
        return None

def create_checksum_metadata(file_path, checksum):
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
            "exportedBy": "WiFi GeoGrabber Python Map Viewer"
        }
        
        return metadata
    except Exception as e:
        print(f"Error creating metadata: {e}")
        return None

def save_checksum_metadata(db_path, metadata):
    """Saves checksum metadata to a .sha256.json file"""
    try:
        checksum_path = db_path + ".sha256.json"
        with open(checksum_path, 'w') as f:
            json.dump(metadata, f, indent=2)
        return checksum_path
    except Exception as e:
        print(f"Error saving metadata: {e}")
        return None

def verify_checksum_from_metadata(db_path, metadata_path):
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
        actual_checksum = calculate_sha256_checksum(db_path)
        expected_checksum = metadata.get("checksum")
        
        if actual_checksum != expected_checksum:
            return False, "Checksum verification failed! File may have been modified or corrupted."
        
        return True, "Checksum verification successful!"
        
    except Exception as e:
        return False, f"Error during verification: {str(e)}"

def get_vendor_from_oui(bssid):
    """Determines the manufacturer based on the OUI (first 6 characters of the MAC address)"""
    if not bssid or len(bssid) < 8:
        return "Unknown"

    oui = bssid[:8].upper().replace(":", "").replace("-", "")
    if len(oui) < 6:
        return "Unknown"

    # Known manufacturer OUIs
    vendor_db = {
        "005056": "VMware",
        "000C29": "VMware", 
        "001C42": "Parallels",
        "080027": "VirtualBox",
        "F01898": "Apple",
        "ACBC32": "Apple",
        "00236C": "Apple",
        "8C8590": "Apple",
        "40CBC0": "Apple",
        "907240": "Apple",
        "B8E856": "Apple",
        "0050F2": "Microsoft",
        "00155D": "Microsoft",
        "A0999B": "Microsoft",
        "001DD8": "NETGEAR",
        "A00460": "NETGEAR",
        "204E7F": "NETGEAR",
        "C40415": "NETGEAR",
        "00223F": "Linksys",
        "C05627": "Linksys",
        "48F8B3": "Linksys",
        "0014BF": "Linksys",
        "302303": "D-Link",
        "0015E9": "D-Link",
        "00265A": "D-Link",
        "BCF685": "D-Link",
        "000B6B": "Intel",
        "001302": "Intel",
        "001500": "Intel",
        "0016EA": "Intel",
        "0019D1": "Intel",
        "001B77": "Intel",
        "00216A": "Intel",
        "0024D7": "Intel",
        "A434D9": "Intel",
        "0024E9": "Samsung",
        "002637": "Samsung",
        "A8F274": "Samsung",
        "F47B09": "Samsung",
        "183A2D": "Samsung",
        "E8508B": "Samsung",
        "001A11": "Google",
        "DAA119": "Google",
        "F88FCA": "Google",
        "AC3743": "HTC",
        "002376": "HTC",
        "6021C0": "HTC",
        "188796": "Cisco",
        "000A41": "Cisco",
        "00D0BC": "Cisco",
        "00D058": "Cisco",
        "B8BEBF": "Cisco"
    }
    
    oui_key = oui[:6]
    return vendor_db.get(oui_key, f"Unknown ({bssid[:8]})")

def select_database_file():
    """Opens a file selection dialog for .db files with optional checksum verification"""
    root = tk.Tk()
    root.withdraw()  # Hides the main window

    # Start directory is the program folder
    initial_dir = os.path.dirname(os.path.abspath(__file__))

    file_path = filedialog.askopenfilename(
        title="Select a WiFi Scanner Database",
        initialdir=initial_dir,
        filetypes=[("Database files", "*.db"), ("All files", "*.*")]
    )
    
    if not file_path:
        root.destroy()
        return None
    
    # Check if checksum metadata file exists in same directory
    checksum_path = file_path + ".sha256.json"
    if os.path.exists(checksum_path):
        # Ask user if they want to verify with the found checksum file
        verify = messagebox.askyesno(
            "Checksum Verification Available",
            f"A checksum metadata file was found:\n{os.path.basename(checksum_path)}\n\n"
            "Would you like to verify the database integrity before loading?",
            parent=root
        )
        
        if verify:
            success, message = verify_checksum_from_metadata(file_path, checksum_path)
            if success:
                messagebox.showinfo("Verification Success", message, parent=root)
            else:
                messagebox.showerror("Verification Failed", 
                    f"{message}\n\nThe database may have been modified or corrupted.\n"
                    "Loading is not recommended!",
                    parent=root)
                
                # Ask if user wants to continue anyway
                continue_anyway = messagebox.askyesno(
                    "Continue Anyway?",
                    "Do you want to load the database despite the failed verification?",
                    parent=root
                )
                
                if not continue_anyway:
                    root.destroy()
                    return None
    else:
        # No checksum file found automatically, ask if user wants to select one manually
        select_checksum = messagebox.askyesno(
            "No Checksum Found",
            "No checksum metadata file was found automatically.\n\n"
            "Would you like to select a checksum file manually for verification?",
            parent=root
        )
        
        if select_checksum:
            # Let user select checksum file
            checksum_dir = os.path.dirname(file_path)
            manual_checksum_path = filedialog.askopenfilename(
                title="Select Checksum Metadata File",
                initialdir=checksum_dir,
                filetypes=[("Checksum files", "*.sha256.json"), ("JSON files", "*.json"), ("All files", "*.*")],
                parent=root
            )
            
            if manual_checksum_path:
                print("Verifying with selected checksum file...")
                success, message = verify_checksum_from_metadata(file_path, manual_checksum_path)
                if success:
                    messagebox.showinfo("Verification Success", message, parent=root)
                else:
                    messagebox.showerror("Verification Failed", 
                        f"{message}\n\nThe database may have been modified or corrupted.\n"
                        "Loading is not recommended!",
                        parent=root)
                    
                    # Ask if user wants to continue anyway
                    continue_anyway = messagebox.askyesno(
                        "Continue Anyway?",
                        "Do you want to load the database despite the failed verification?",
                        parent=root
                    )
                    
                    if not continue_anyway:
                        root.destroy()
                        return None
        else:
            # User doesn't want to select a checksum file, ask if they want to create one
            create = messagebox.askyesno(
                "Create Checksum?",
                "Would you like to create a new checksum metadata file for this database?\n\n"
                "This will help verify the database integrity in the future.",
                parent=root
            )
            
            if create:
                print("Calculating SHA-256 checksum...")
                checksum = calculate_sha256_checksum(file_path)
                if checksum:
                    metadata = create_checksum_metadata(file_path, checksum)
                    if metadata:
                        saved_path = save_checksum_metadata(file_path, metadata)
                        if saved_path:
                            messagebox.showinfo(
                                "Checksum Created",
                                f"Checksum metadata saved to:\n{os.path.basename(saved_path)}",
                                parent=root
                            )
                        else:
                            messagebox.showwarning("Warning", "Failed to save checksum metadata", parent=root)
    
    root.destroy()
    return file_path

def load_wifi_data(db_path, passphrase=None):
    """Loads WiFi and Bluetooth data from the SQLite database (encrypted or unencrypted)"""
    try:
        # Check if encryption is supported
        if ENCRYPTION_SUPPORT:
            db_enc = DatabaseEncryption(db_path)
            
            # Check if database is encrypted
            if db_enc.is_database_encrypted():
                print("🔒 Database is encrypted (SQLCipher format)")
                
                # If no passphrase provided, prompt for it
                if not passphrase:
                    root = tk.Tk()
                    root.withdraw()
                    
                    passphrase = simpledialog.askstring(
                        "Database Encrypted",
                        "This database is encrypted.\nPlease enter your passphrase:",
                        show='*'
                    )
                    
                    root.destroy()
                    
                    if not passphrase:
                        messagebox.showerror("Error", "No passphrase provided. Cannot open encrypted database.")
                        return []
                
                # Connect with encryption
                if not db_enc.connect_encrypted(passphrase):
                    messagebox.showerror("Error", "Wrong passphrase or corrupted database!")
                    return []
                
                conn = db_enc.get_connection()
            else:
                # Unencrypted database
                print("Database is unencrypted (standard SQLite)")
                if not db_enc.connect_unencrypted():
                    messagebox.showerror("Error", "Failed to open database!")
                    return []
                conn = db_enc.get_connection()
        else:
            # No encryption support, try standard SQLite
            conn = sqlite3.connect(db_path)
        
        cursor = conn.cursor()

        # Check first if the new device_data table exists
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='device_data'")
        has_device_table = cursor.fetchone() is not None
        
        all_data = []
        
        if has_device_table:
            # Check available columns in device_data table
            cursor.execute("PRAGMA table_info(device_data)")
            device_columns = [col[1] for col in cursor.fetchall()]
            has_movement_tracking = 'last_seen_latitude' in device_columns
            has_new_fields = 'capabilities' in device_columns

            # Only load Bluetooth data from device_data (WiFi is in wifi_data)
            # Load all Bluetooth devices (including Unknown Device for "Bluetooth" filter)
            if has_movement_tracking and has_new_fields:
                query = """
                SELECT device_name, device_address, device_type, signal_strength, 
                       encryption_info, latitude, longitude, timestamp, frequency, 
                       channel, wifi_standard, vendor_info, channel_width, max_connection_speed,
                       capabilities, center_freq0, center_freq1, 
                       is_passpoint_network, operator_friendly_name, venue_name,
                       last_seen_latitude, last_seen_longitude, last_seen_timestamp, movement_distance
                FROM device_data 
                WHERE latitude != 0 AND longitude != 0 AND device_type = 'BLUETOOTH'
                ORDER BY device_type, signal_strength DESC
                """
            elif has_movement_tracking:
                query = """
                SELECT device_name, device_address, device_type, signal_strength, 
                       encryption_info, latitude, longitude, timestamp, frequency, 
                       channel, wifi_standard, vendor_info, channel_width, max_connection_speed,
                       last_seen_latitude, last_seen_longitude, last_seen_timestamp, movement_distance
                FROM device_data 
                WHERE latitude != 0 AND longitude != 0 AND device_type = 'BLUETOOTH'
                ORDER BY device_type, signal_strength DESC
                """
            else:
                query = """
                SELECT device_name, device_address, device_type, signal_strength, 
                       encryption_info, latitude, longitude, timestamp, frequency, 
                       channel, wifi_standard, vendor_info, channel_width, max_connection_speed
                FROM device_data 
                WHERE latitude != 0 AND longitude != 0 AND device_type = 'BLUETOOTH'
                ORDER BY device_type, signal_strength DESC
                """
            
            cursor.execute(query)
            device_data = cursor.fetchall()

            # Convert to extended format
            for row in device_data:
                if has_movement_tracking and has_new_fields and len(row) >= 24:  # New structure with all new fields
                    device_name, device_address, device_type, signal_strength, encryption_info, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed, capabilities, center_freq0, center_freq1, is_passpoint_network, operator_friendly_name, venue_name, last_seen_lat, last_seen_lon, last_seen_timestamp, movement_distance = row
                    # Use vendor info from DB, if not available derive from MAC
                    if not vendor_info or vendor_info == "Unknown":
                        vendor_info = get_vendor_from_oui(device_address)
                    
                    all_data.append({
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
                        'source_table': 'device_data'
                    })
                elif has_movement_tracking and len(row) >= 18:  # Structure with movement tracking
                    device_name, device_address, device_type, signal_strength, encryption_info, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed, last_seen_lat, last_seen_lon, last_seen_timestamp, movement_distance = row
                elif has_movement_tracking and len(row) >= 18:  # Structure with movement tracking
                    device_name, device_address, device_type, signal_strength, encryption_info, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed, last_seen_lat, last_seen_lon, last_seen_timestamp, movement_distance = row
                    # Use vendor info from DB, if not available derive from MAC
                    if not vendor_info or vendor_info == "Unknown":
                        vendor_info = get_vendor_from_oui(device_address)
                    
                    all_data.append({
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
                        'capabilities': None,
                        'center_freq0': None,
                        'center_freq1': None,
                        'is_passpoint': None,
                        'operator_name': None,
                        'venue_name': None,
                        'last_seen_lat': last_seen_lat,
                        'last_seen_lon': last_seen_lon,
                        'last_seen_timestamp': last_seen_timestamp,
                        'movement_distance': movement_distance,
                        'source_table': 'device_data'
                    })
                elif len(row) >= 14:  # Extended data available (without movement tracking)
                    device_name, device_address, device_type, signal_strength, encryption_info, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed = row
                    # Use vendor info from DB, if not available derive from MAC
                    if not vendor_info or vendor_info == "Unknown":
                        vendor_info = get_vendor_from_oui(device_address)
                    
                    all_data.append({
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
                        'capabilities': None,
                        'center_freq0': None,
                        'center_freq1': None,
                        'is_passpoint': None,
                        'operator_name': None,
                        'venue_name': None,
                        'last_seen_lat': None,
                        'last_seen_lon': None,
                        'last_seen_timestamp': None,
                        'movement_distance': None,
                        'source_table': 'device_data'
                    })
                else:  # Old data structure
                    device_name, device_address, device_type, signal_strength, encryption_info, lat, lon, timestamp = row[:8]
                    vendor_info = get_vendor_from_oui(device_address)
                    
                    all_data.append({
                        'name': device_name,
                        'address': device_address,
                        'type': device_type,
                        'signal': signal_strength,
                        'encryption': encryption_info,
                        'lat': lat,
                        'lon': lon,
                        'timestamp': timestamp,
                        'frequency': None,
                        'channel': None,
                        'standard': None,
                        'vendor': vendor_info,
                        'channel_width': None,
                        'max_speed': None,
                        'capabilities': None,
                        'center_freq0': None,
                        'center_freq1': None,
                        'is_passpoint': None,
                        'operator_name': None,
                        'venue_name': None,
                        'last_seen_lat': None,
                        'last_seen_lon': None,
                        'last_seen_timestamp': None,
                        'movement_distance': None,
                        'source_table': 'device_data'
                    })

        # Check if wifi_data table exists
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='wifi_data'")
        has_wifi_table = cursor.fetchone() is not None

        if has_wifi_table:
            # Load all WiFi data from wifi_data
            # Check if extended fields exist in wifi_data
            cursor.execute("PRAGMA table_info(wifi_data)")
            columns = [column[1] for column in cursor.fetchall()]
            has_extended_fields = 'frequency' in columns
            has_new_fields = 'capabilities' in columns
            
            if has_extended_fields and has_new_fields:
                query = """
                SELECT ssid, bssid, signal_strength, encryption, 
                       latitude, longitude, timestamp, frequency, channel, 
                       wifi_standard, vendor_info, channel_width, max_connection_speed,
                       capabilities, center_freq0, center_freq1
                FROM wifi_data 
                WHERE latitude != 0 AND longitude != 0
                ORDER BY signal_strength DESC
                """
            elif has_extended_fields:
                query = """
                SELECT ssid, bssid, signal_strength, encryption, 
                       latitude, longitude, timestamp, frequency, channel, 
                       wifi_standard, vendor_info, channel_width, max_connection_speed
                FROM wifi_data 
                WHERE latitude != 0 AND longitude != 0
                ORDER BY signal_strength DESC
                """
            else:
                query = """
                SELECT ssid, bssid, signal_strength, encryption, 
                       latitude, longitude, timestamp
                FROM wifi_data 
                WHERE latitude != 0 AND longitude != 0
                ORDER BY signal_strength DESC
                """
            
            cursor.execute(query)
            wifi_data = cursor.fetchall()

            # Convert WiFi data
            for row in wifi_data:
                if has_extended_fields and has_new_fields and len(row) >= 16:
                    ssid, bssid, signal_strength, encryption, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed, capabilities, center_freq0, center_freq1 = row[:16]
                    if not vendor_info or vendor_info == "Unknown":
                        vendor_info = get_vendor_from_oui(bssid)
                    
                    all_data.append({
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
                        'is_passpoint': None,
                        'operator_name': None,
                        'venue_name': None,
                        'last_seen_lat': None,
                        'last_seen_lon': None,
                        'last_seen_timestamp': None,
                        'movement_distance': None,
                        'source_table': 'wifi_data'
                    })
                elif has_extended_fields and len(row) >= 13:
                    ssid, bssid, signal_strength, encryption, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed = row[:13]
                    if not vendor_info or vendor_info == "Unknown":
                        vendor_info = get_vendor_from_oui(bssid)
                    
                    all_data.append({
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
                        'capabilities': None,
                        'center_freq0': None,
                        'center_freq1': None,
                        'is_passpoint': None,
                        'operator_name': None,
                        'venue_name': None,
                        'last_seen_lat': None,
                        'last_seen_lon': None,
                        'last_seen_timestamp': None,
                        'movement_distance': None,
                        'source_table': 'wifi_data'
                    })
                else:
                    ssid, bssid, signal_strength, encryption, lat, lon, timestamp = row[:7]
                    frequency = channel = wifi_standard = channel_width = max_speed = None
                    vendor_info = get_vendor_from_oui(bssid)
                    
                    all_data.append({
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
                        'capabilities': None,
                        'center_freq0': None,
                        'center_freq1': None,
                        'is_passpoint': None,
                        'operator_name': None,
                        'venue_name': None,
                        'last_seen_lat': None,
                        'last_seen_lon': None,
                        'last_seen_timestamp': None,
                        'movement_distance': None,
                        'source_table': 'wifi_data'
                    })

        conn.close()

        # Deduplication: Remove duplicates based on BSSID/MAC address
        # Prefer newest entries (highest timestamp) and better signal strength
        seen_devices = {}
        deduplicated_data = []
        
        for device in all_data:
            device_key = f"{device['address']}_{device['type']}"
            
            if device_key in seen_devices:
                existing = seen_devices[device_key]

                # Compare timestamp first (newest wins)
                if device['timestamp'] > existing['timestamp']:
                    # Newer entry - replace
                    seen_devices[device_key] = device
                elif device['timestamp'] == existing['timestamp']:
                    # Same timestamp - compare signal
                    current_signal = existing.get('signal', -999)
                    new_signal = device.get('signal', -999)
                    if new_signal > current_signal:
                        seen_devices[device_key] = device
                # Otherwise keep the existing one
            else:
                seen_devices[device_key] = device

        # Create final list without duplicates
        deduplicated_data = list(seen_devices.values())

        print(f"Before deduplication: {len(all_data)} devices")
        print(f"After deduplication: {len(deduplicated_data)} devices")
        print(f"Removed duplicates: {len(all_data) - len(deduplicated_data)}")
        
        return deduplicated_data

    except Exception as e:
        messagebox.showerror("Database Error", f"Error loading database: {str(e)}")
        return []

def update_device_location_in_db(db_path, device_address, device_type, new_lat, new_lon, passphrase=None):
    """Updates the GPS coordinates of a device in the database (encrypted or unencrypted)"""
    try:
        # Check if encryption is supported and database is encrypted
        if ENCRYPTION_SUPPORT:
            db_enc = DatabaseEncryption(db_path)
            
            if db_enc.is_database_encrypted():
                if not passphrase:
                    print("Warning: Cannot update encrypted database without passphrase")
                    return False
                
                if not db_enc.connect_encrypted(passphrase):
                    print("Error: Wrong passphrase for encrypted database")
                    return False
                
                conn = db_enc.get_connection()
            else:
                if not db_enc.connect_unencrypted():
                    return False
                conn = db_enc.get_connection()
        else:
            conn = sqlite3.connect(db_path)
        
        cursor = conn.cursor()

        # Check which tables exist
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='device_data'")
        has_device_table = cursor.fetchone() is not None
        
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='wifi_data'")
        has_wifi_table = cursor.fetchone() is not None

        updated_rows = 0

        # Update in device_data table if available
        if has_device_table:
            cursor.execute("""
                UPDATE device_data 
                SET latitude = ?, longitude = ?
                WHERE device_address = ? AND device_type = ?
            """, (new_lat, new_lon, device_address, device_type))
            updated_rows += cursor.rowcount

        # Update in wifi_data table if WiFi device and table available
        if has_wifi_table and device_type == 'WIFI':
            cursor.execute("""
                UPDATE wifi_data 
                SET latitude = ?, longitude = ?
                WHERE bssid = ?
            """, (new_lat, new_lon, device_address))
            updated_rows += cursor.rowcount
        
        conn.commit()
        conn.close()
        
        return updated_rows > 0

    except Exception as e:
        print(f"Error updating device position: {str(e)}")
        return False

def create_wifi_map(device_data, db_filename, db_path=None):
    """Creates an interactive map with WiFi and Bluetooth data"""
    if not device_data:
        messagebox.showwarning("No Data", "No devices with valid GPS coordinates found!")
        return None

    # Calculate map center (average of all coordinates)
    avg_lat = sum(device['lat'] for device in device_data) / len(device_data)
    avg_lon = sum(device['lon'] for device in device_data) / len(device_data)

    # Create the map
    device_map = folium.Map(
        location=[avg_lat, avg_lon],
        zoom_start=15,
        tiles='OpenStreetMap'
    )

    # Create a single FeatureGroup for all markers
    all_devices_group = folium.FeatureGroup(name="All Devices")

    # Filter categories for JavaScript-based filtering
    filter_categories = {
        'wifi_open': 'WiFi Open',
        'wifi_encrypted': 'WiFi Encrypted',
        'bluetooth': 'Bluetooth',
        'bluetooth_known': 'Bluetooth without Unknown',
        'devices_with_movement': 'Devices with Movement',
        'signal_very_strong': 'Signal Very Strong (≥-50 dBm)',
        'signal_strong': 'Signal Strong (-50 to -70 dBm)',
        'signal_medium': 'Signal Medium (-70 to -80 dBm)',
        'signal_weak': 'Signal Weak (<-80 dBm)',
        'vodafone': 'Vodafone Homespot/Hotspot',
        'wifi_open_no_vodafone': 'WiFi Open without Vodafone'
    }

    # Count different device types
    wifi_open = 0
    wifi_encrypted = 0
    bluetooth_count = 0
    bluetooth_known_count = 0
    vodafone_homespot_count = 0
    wifi_open_no_vodafone = 0

    # Calculate filter classes for each device in advance
    device_filters = []
    for device in device_data:
        filter_classes = []

        # By device type and encryption
        if device['type'] == "WIFI":
            if device['encryption'] == "open":
                filter_classes.append('wifi_open')
                # WiFi Open without Vodafone - only if it's NOT a Vodafone Homespot
                if not (device['name'] and ("vodafone homespot" in device['name'].lower() or "vodafone hotspot" in device['name'].lower())):
                    filter_classes.append('wifi_open_no_vodafone')
            else:
                filter_classes.append('wifi_encrypted')
        else:  # Bluetooth
            filter_classes.append('bluetooth')
            # Bluetooth without Unknown Device in separate group
            if device['name'] and device['name'] not in ["[Unknown Device]", "Unknown Device"]:
                filter_classes.append('bluetooth_known')

        # Vodafone Homespot in separate group
        if device['type'] == "WIFI" and device['name'] and ("vodafone homespot" in device['name'].lower() or "vodafone hotspot" in device['name'].lower()):
            filter_classes.append('vodafone')

        # By movement - check if device has movement data
        if device.get('last_seen_lat') is not None and device.get('last_seen_lon') is not None:
            filter_classes.append('devices_with_movement')

        # By signal strength
        if device['signal'] is not None:
            if device['signal'] >= -50:
                filter_classes.append('signal_very_strong')
            elif device['signal'] >= -70:
                filter_classes.append('signal_strong')
            elif device['signal'] >= -80:
                filter_classes.append('signal_medium')
            else:
                filter_classes.append('signal_weak')
        else:
            filter_classes.append('signal_weak')  # Fallback for unknown signals

        device_filters.append(filter_classes)

    # No distribution of overlapping markers anymore, all markers stay exactly on their database coordinates
    device_data_distributed = device_data.copy()

    # Add all markers to a single group with filter classes
    for i, device in enumerate(device_data_distributed):
        filter_classes = device_filters[i]  # Use pre-calculated filter classes
        device_name = device['name']
        device_address = device['address']
        device_type = device['type']
        signal = device['signal']
        encryption_info = device['encryption']
        lat = device['lat']
        lon = device['lon']
        timestamp = device['timestamp']
        # No distribution anymore, so always False and original = current coordinates
        is_distributed = False
        original_lat = lat
        original_lon = lon

        # Dynamic circle radius depending on signal strength (dBm)
        if signal is not None:
            if signal >= -50:
                circle_radius = 10  # very strong
            elif signal >= -70:
                circle_radius = 20  # strong
            elif signal >= -80:
                circle_radius = 30  # medium
            else:
                circle_radius = 40  # weak
        else:
            circle_radius = 30  # fallback

        # Handle device names (if empty)
        if not device_name or device_name.strip() == "":
            if device_type == "WIFI":
                device_name = "[Hidden Network]"
            else:
                device_name = "[Unknown Device]"

        # Does the device have movement data?
        has_movement_data = device.get('last_seen_lat') is not None and device.get('last_seen_lon') is not None

        # Icon and color based on device type and encryption
        if device_type == "WIFI":
            if encryption_info == "open":
                icon_color = "red"
                icon_name = "wifi"
                wifi_open += 1
                # Count WiFi open without Vodafone separately (excludes Homespot AND Hotspot)
                if not (device_name and ("vodafone homespot" in device_name.lower() or "vodafone hotspot" in device_name.lower())):
                    wifi_open_no_vodafone += 1
            else:
                icon_color = "green"
                icon_name = "lock"
                wifi_encrypted += 1
        else:  # Bluetooth
            icon_color = "blue"
            icon_name = "bluetooth"
            bluetooth_count += 1
            # Count known Bluetooth devices (not "Unknown Device")
            if device_name and device_name not in ["[Unknown Device]", "Unknown Device"]:
                bluetooth_known_count += 1

        # Count Vodafone Homespot and Hotspot separately
        if device_type == "WIFI" and device_name and ("vodafone homespot" in device_name.lower() or "vodafone hotspot" in device_name.lower()):
            vodafone_homespot_count += 1

        # Signal strength rating
        if signal >= -50:
            signal_text = "Very strong"
        elif signal >= -70:
            signal_text = "Strong"
        elif signal >= -80:
            signal_text = "Medium"
        else:
            signal_text = "Weak"

        # Popup text with all information
        if device_type == "WIFI":
            popup_text = f"""
            <b>SSID:</b> {device_name}<br>
            <b>BSSID:</b> {device_address}<br>
            <b>Signal:</b> {signal} dBm ({signal_text})<br>
            <b>Encryption:</b> {encryption_info}<br>
            """

            # Add extended WiFi information if available
            if device.get('frequency'):
                popup_text += f"<b>Frequency:</b> {device['frequency']} MHz<br>"
            if device.get('channel'):
                popup_text += f"<b>Channel:</b> {device['channel']}<br>"
            if device.get('standard'):
                popup_text += f"<b>Standard:</b> {device['standard']}<br>"
            if device.get('channel_width'):
                popup_text += f"<b>Channel Width:</b> {device['channel_width']} MHz<br>"
            if device.get('max_speed'):
                popup_text += f"<b>Max. Speed:</b> {device['max_speed']} Mbps<br>"
            if device.get('vendor'):
                popup_text += f"<b>Manufacturer:</b> {device['vendor']}<br>"


            popup_text += f"""
            <b>Coordinates:</b> {lat:.6f}, {lon:.6f}<br>
            <b>Timestamp:</b> {timestamp}
            """

            # Add movement data if available
            if has_movement_data:
                popup_text += f"""<br><br><b>--- Movement Analysis ---</b><br>
                <b>Last Position:</b> {device['last_seen_lat']:.6f}, {device['last_seen_lon']:.6f}<br>
                <b>Last Seen:</b> {device['last_seen_timestamp']}<br>"""
                if device.get('movement_distance'):
                    popup_text += f"<b>Movement:</b> {device['movement_distance']:.1f} m<br>"
        else:  # Bluetooth
            popup_text = f"""
            <b>Device Name:</b> {device_name}<br>
            <b>MAC Address:</b> {device_address}<br>
            <b>Signal:</b> {signal} dBm ({signal_text})<br>
            <b>Device Type:</b> Bluetooth<br>
            <b>Device Class:</b> {encryption_info}<br>
            """
            if device.get('vendor'):
                popup_text += f"<b>Manufacturer:</b> {device['vendor']}<br>"


            popup_text += f"""
            <b>Coordinates:</b> {lat:.6f}, {lon:.6f}<br>
            <b>Timestamp:</b> {timestamp}
            """

            # Add movement data if available
            if has_movement_data:
                popup_text += f"""<br><br><b>--- Movement Analysis ---</b><br>
                <b>Last Position:</b> {device['last_seen_lat']:.6f}, {device['last_seen_lon']:.6f}<br>
                <b>Last Seen:</b> {device['last_seen_timestamp']}<br>"""
                if device.get('movement_distance'):
                    popup_text += f"<b>Movement:</b> {device['movement_distance']:.1f} m<br>"

        # Tooltip text with distribution note
        tooltip_text = f"{device_name} ({signal} dBm)"
        if device_type == "BLUETOOTH":
            tooltip_text = f"[BT] {tooltip_text}"
        if device.get('vendor') and device['vendor'] != "Unknown" and not device['vendor'].startswith('Unknown ('):
            tooltip_text += f" - {device['vendor']}"


        # Single marker with unique ID for filter assignment
        marker_id = f"marker_{device_address.replace(':', '').replace('-', '')}"
        
        marker_color = icon_color
        if has_movement_data:
            if icon_color == 'red':
                marker_color = 'darkred'
            elif icon_color == 'green':
                marker_color = 'darkgreen'
            elif icon_color == 'blue':
                marker_color = 'darkblue'
        
        marker = folium.Marker(
            location=[lat, lon],
            popup=folium.Popup(popup_text, max_width=400),
            tooltip=tooltip_text,
            icon=folium.Icon(
                color=marker_color,
                icon=icon_name,
                prefix='fa'
            ),
            draggable=True  # Make marker draggable
        )
        
        circle_id = f"circle_{device_address.replace(':', '').replace('-', '')}"
        circle_color = icon_color
        circle = folium.Circle(
            location=[lat, lon],
            radius=circle_radius,
            color=circle_color,
            fill=True,
            fill_color=circle_color,
            fill_opacity=0.2,
            weight=1
        )


        # Add to the single group
        all_devices_group.add_child(marker)
        all_devices_group.add_child(circle)

    # Add single group to map (all markers are in here)
    device_map.add_child(all_devices_group)

    # Add custom Layer Control for JavaScript-based filters and marker control
    filter_control_html = f"""
    <div class="custom-layer-control" style="
        position: absolute;
        top: 10px;
        left: 10px;
        background: white;
        border: 2px solid rgba(0,0,0,0.2);
        border-radius: 5px;
        padding: 10px;
        z-index: 10000;
        min-width: 200px;
        max-height: 450px;
        overflow-y: auto;
        box-shadow: 0 1px 7px rgba(0,0,0,0.4);
    ">
        <h4 style="margin: 0 0 10px 0; font-size: 14px;">Marker Control</h4>
        <button onclick="resetAllMarkers()" style="margin: 2px; padding: 6px 12px; font-size: 12px; background: #ff6b6b; color: white; border: none; border-radius: 3px; cursor: pointer;">Reset Markers</button>
        <button onclick="saveAllMarkers()" style="margin: 2px; padding: 6px 12px; font-size: 12px; background: #4ecdc4; color: white; border: none; border-radius: 3px; cursor: pointer;">Save Positions</button>
        <button onclick="debugMarkers()" style="margin: 2px; padding: 6px 12px; font-size: 12px; background: #9b59b6; color: white; border: none; border-radius: 3px; cursor: pointer;">Debug Markers</button>
        <br>
        <button onclick="toggleBluetoothMode()" style="margin: 5px 2px 2px 2px; padding: 6px 12px; font-size: 12px; background: #6c5ce7; color: white; border: none; border-radius: 3px; cursor: pointer;" id="bluetooth-mode-btn">BT-Proximity: Known Only</button>
        <div id="save-status" style="font-size: 11px; margin: 5px 0; min-height: 20px;"></div>

        <hr style="margin: 10px 0;">
        <h4 style="margin: 0 0 10px 0; font-size: 14px;">Filters</h4>
        """ + "".join([f'''
        <label style="display: block; margin: 5px 0; font-size: 12px; cursor: pointer;">
            <input type="checkbox" id="filter_{key}" onchange="toggleFilter('{key}')" style="margin-right: 8px;">
            {label}
        </label>
        ''' for key, label in filter_categories.items()]) + """
        <hr style="margin: 10px 0;">
        <button onclick="toggleAllFilters(true)" style="margin: 2px; padding: 4px 8px; font-size: 11px;">All On</button>
        <button onclick="toggleAllFilters(false)" style="margin: 2px; padding: 4px 8px; font-size: 11px;">All Off</button>
    </div>
    """

    device_map.get_root().html.add_child(folium.Element(filter_control_html))

    # Add CSS and JavaScript for search function and layer control
    search_and_control_js = f"""
    <style>
    /* Hide all overlay layers at start */
    .leaflet-control-layers-overlays input[type="checkbox"] {{
        /* All checkboxes are not activated by default */
    }}

    /* Hide all layer groups when loading */
    .leaflet-overlay-pane svg g {{
        display: none;
    }}

    /* Search box styling */
    .search-container {{
        position: fixed;
        top: 10px;
        left: 50%;
        transform: translateX(-50%);
        z-index: 10000;
        background: white;
        border: 2px solid #ccc;
        border-radius: 5px;
        padding: 10px;
        box-shadow: 0 2px 10px rgba(0,0,0,0.3);
        width: 300px;
    }}
    
    .search-input {{
        width: 100%;
        padding: 8px;
        border: 1px solid #ddd;
        border-radius: 3px;
        font-size: 14px;
    }}
    
    .search-results {{
        max-height: 200px;
        overflow-y: auto;
        margin-top: 5px;
        border: 1px solid #ddd;
        border-radius: 3px;
        background: white;
        display: none;
    }}
    
    .search-result-item {{
        padding: 8px;
        cursor: pointer;
        border-bottom: 1px solid #eee;
    }}
    
    .search-result-item:hover {{
        background-color: #f0f0f0;
    }}
    
    .search-result-item:last-child {{
        border-bottom: none;
    }}

    .search-count {{
        font-size: 12px;
        color: #666;
        margin-top: 5px;
    }}

    /* Drag-Info styling */
    .drag-info {{
        position: fixed;
        bottom: 10px;
        left: 10px;
        background: rgba(0, 0, 0, 0.8);
        color: white;
        padding: 8px 12px;
        border-radius: 5px;
        font-size: 12px;
        z-index: 10000;
    }}
    
    /* Middle-click Info */
    .middle-click-info {{
        position: fixed;
        bottom: 40px;
        left: 10px;
        background: rgba(0, 100, 200, 0.9);
        color: white;
        padding: 6px 10px;
        border-radius: 5px;
        font-size: 11px;
        z-index: 10000;
        max-width: 320px;
        line-height: 1.3;
    }}
    
    /* Marker Cursor Hint */
    .leaflet-marker-icon {{
        cursor: pointer !important;
    }}
    
    .leaflet-marker-icon:hover {{
        filter: brightness(1.1);
    }}
    </style>

    <!-- Search box HTML -->
    <div class="search-container">
        <input type="text" class="search-input" placeholder="Search SSID or MAC (e.g. 'Eduard' or 'db:7a')..." id="searchInput">
        <div class="search-count" id="searchCount"></div>
        <div class="search-results" id="searchResults"></div>
    </div>

    <!-- Drag Info -->
    <div class="drag-info">
        💡 Tip: Markers can be moved via drag & drop
    </div>

    <!-- Middle-Click Info -->
    <div class="middle-click-info">
        🖱️ Middle mouse button or right click: Highlight marker + SSID in search bar<br>
        🔄 Click same marker again: Remove highlight
    </div>
    
    <script>
    // Create filter mapping from device data
    let deviceFilterMap = new Map();
    """ + "".join([f"""
    deviceFilterMap.set('{device["address"]}', {json.dumps(device_filters[i])});""" 
    for i, device in enumerate(device_data_distributed)]) + """

    // Global variables - Simplified search data with distributed coordinates
    let searchData = """ + json.dumps([{
        'name': device['name'] if device['name'] and device['name'].strip() else '[Hidden Network]',
        'address': device['address'] if device['address'] else 'Unknown',
        'lat': float(device['lat']),
        'lon': float(device['lon']),
        'type': device['type'],
        'signal': int(device['signal']) if device['signal'] is not None else -100,
        'encryption': device['encryption'] if device['encryption'] else 'Unknown',
        'is_distributed': device.get('is_distributed', False),
        'original_lat': float(device.get('original_lat', device['lat'])),
        'original_lon': float(device.get('original_lon', device['lon'])),
        'last_seen_lat': device.get('last_seen_lat'),
        'last_seen_lon': device.get('last_seen_lon'),
        'last_seen_timestamp': device.get('last_seen_timestamp')
    } for device in device_data_distributed], ensure_ascii=False) + """;
    
    let highlightedMarkers = [];
    let map = null;
    let pendingUpdates = [];
    let activeFilters = new Set(); // Active filters
    let allMarkers = []; // References to all markers
    let allCircles = []; // References to all circles
    let currentlyHighlightedDevice = null; // Currently highlighted device for toggle function
    let movementLayer = null; // Layer for movement line and second marker
    let bluetoothMode = 'known'; // 'known', 'all', 'none' - Default: only known Bluetooth devices
    
    console.log('Search data loaded:', searchData.length, 'devices');

    // Global data for drag events with distributed coordinates
    let deviceDataMap = new Map();
    """ + "".join([f"""
    deviceDataMap.set('{device["address"]}', {{
        address: '{device["address"]}', 
        type: '{device["type"]}'
    }});""" for device in device_data_distributed]) + """

    // Filter functions
    function toggleFilter(filterKey) {
        const checkbox = document.getElementById('filter_' + filterKey);
        if (checkbox.checked) {
            activeFilters.add(filterKey);
        } else {
            activeFilters.delete(filterKey);
        }
        updateMarkerVisibility();
    }
    
    function toggleAllFilters(enable) {
        const checkboxes = document.querySelectorAll('.custom-layer-control input[type="checkbox"]');
        activeFilters.clear();
        
        checkboxes.forEach(checkbox => {
            checkbox.checked = enable;
            if (enable) {
                const filterKey = checkbox.id.replace('filter_', '');
                activeFilters.add(filterKey);
            }
        });
        
        updateMarkerVisibility();
    }
    
    function updateMarkerVisibility() {
        // If no filters are active, hide all
        if (activeFilters.size === 0) {
            allMarkers.forEach(marker => {
                if (marker._map) {
                    marker._map.removeLayer(marker);
                }
            });
            allCircles.forEach(circle => {
                if (circle._map) {
                    circle._map.removeLayer(circle);
                }
            });
            return;
        }

        // Iterate through all devices and check filters
        searchData.forEach((device, index) => {
            const deviceFilters = deviceFilterMap.get(device.address) || [];
            let shouldShow = deviceFilters.some(cls => activeFilters.has(cls));
            // Bluetooth filter logic
            if (device.type === 'BLUETOOTH') {
                const isUnknown = (device.name === '[Unknown Device]' || device.name === 'Unknown Device' || device.name === '' || device.name == null);
                // Only bluetooth_known active: only known devices
                if (activeFilters.has('bluetooth_known') && activeFilters.size === 1) {
                    shouldShow = !isUnknown;
                }
                // Only bluetooth active: all devices
                else if (activeFilters.has('bluetooth') && activeFilters.size === 1) {
                    shouldShow = true;
                }
                // Both active: only known devices
                else if (activeFilters.has('bluetooth') && activeFilters.has('bluetooth_known') && activeFilters.size === 2) {
                    shouldShow = !isUnknown;
                }
                // bluetooth_known + other filters: only show Unknown Devices if another filter (except bluetooth_known) applies to this device
                else if (activeFilters.has('bluetooth_known') && isUnknown) {
                    const otherActive = deviceFilters.some(cls => cls !== 'bluetooth_known' && activeFilters.has(cls));
                    shouldShow = otherActive;
                }
            }
            const marker = allMarkers[index];
            const circle = allCircles[index];
            if (shouldShow) {
                // Show marker
                if (marker && !marker._map) {
                    marker.addTo(map);
                }
                if (circle && !circle._map) {
                    circle.addTo(map);
                }
            } else {
                // Hide marker
                if (marker && marker._map) {
                    marker._map.removeLayer(marker);
                }
                if (circle && circle._map) {
                    circle._map.removeLayer(circle);
                }
            }
        });
    }

    // Toggle Bluetooth mode
    function toggleBluetoothMode() {
        const button = document.getElementById('bluetooth-mode-btn');

        // Switch between modes
        if (bluetoothMode === 'known') {
            bluetoothMode = 'all';
            button.textContent = 'BT-Proximity: All';
            button.style.background = '#74b9ff';
        } else if (bluetoothMode === 'all') {
            bluetoothMode = 'none';
            button.textContent = 'BT-Proximity: None';
            button.style.background = '#636e72';
        } else {
            bluetoothMode = 'known';
            button.textContent = 'BT-Proximity: Known Only';
            button.style.background = '#6c5ce7';
        }

        // If a device is highlighted, update the display
        if (currentlyHighlightedDevice) {
            // Remove old Bluetooth markers and create new ones
            const currentHighlights = [...highlightedMarkers];
            clearHighlights();

            // Find the device and highlight it again with new settings
            const device = searchData.find(d =>
                d.address === currentlyHighlightedDevice.address &&
                d.type === currentlyHighlightedDevice.type
            );

            if (device) {
                highlightDeviceByObject(device, 'Bluetooth mode changed');
            }
        }

        console.log(`Bluetooth mode changed to: ${bluetoothMode}`);
    }

    // Wait until the map is loaded
    document.addEventListener('DOMContentLoaded', function() {
        console.log('DOM loaded, setting up search...');

        // Find the Leaflet map
        setTimeout(function() {
            // Find map reference - better method
            if (typeof window.map_1 !== 'undefined') {
                map = window.map_1;
            } else {
                // Fallback: Search for Leaflet map instance
                let mapElements = document.querySelectorAll('.leaflet-container');
                if (mapElements.length > 0) {
                    let mapId = mapElements[0].id;
                    if (window[mapId]) {
                        map = window[mapId];
                    }
                }
            }

            console.log('Map found:', map ? 'Yes' : 'No');

            // Collect all markers and circles for filter control
            map.eachLayer(function(layer) {
                if (layer instanceof L.Marker) {
                    allMarkers.push(layer);
                } else if (layer instanceof L.Circle) {
                    allCircles.push(layer);
                }
            });

            // Actually set all markers as draggable (Leaflet)
            allMarkers.forEach(function(marker, idx) {
                // Store device data directly on marker
                marker.deviceAddress = searchData[idx].address;
                marker.deviceType = searchData[idx].type;
                if (!marker.options.draggable) {
                    marker.options.draggable = true;
                }
                if (marker.dragging) {
                    marker.dragging.enable();
                    console.log('Marker draggable enabled:', idx, marker.options.draggable, marker.dragging.enabled());
                } else {
                    console.warn('Marker has no dragging property:', idx);
                }
            });

            console.log('Found markers:', allMarkers.length, 'circles:', allCircles.length);

            // Initially hide all markers (since no filters are active)
            updateMarkerVisibility();

            setupSearch();
            setupDragHandlers();

            // Register drag-end event for all markers in layer
            map.eachLayer(function(layer) {
                if (layer instanceof L.Marker && layer.options.draggable && layer.dragging) {
                    layer.on('dragend', function(e) {
                        var newLat = e.target.getLatLng().lat;
                        var newLon = e.target.getLatLng().lng;
                        var address = e.target.deviceAddress;
                        var typ = e.target.deviceType;
                        if (address && typ) {
                            console.log('DragEnd (global): Device directly on marker:', address, typ, newLat, newLon);
                            updateDeviceLocation(address, typ, newLat, newLon);
                            console.log('pendingUpdates:', pendingUpdates.length);
                        } else {
                            console.warn('DragEnd (global): No device data on marker!', newLat, newLon);
                        }
                    });
                }
            });
        }, 1500);
    });

    // Setup drag event and click handler for all markers
    function setupDragHandlers() {
        setTimeout(function() {
            // Find all markers on the map
            map.eachLayer(function(layer) {
                if (layer instanceof L.Marker && layer.options.draggable) {
                    // Drag-Event Handler
                    layer.on('dragend', function(e) {
                        var newLat = e.target.getLatLng().lat;
                        var newLon = e.target.getLatLng().lng;
                        var markerIndex = allMarkers.indexOf(e.target);
                        var device = null;
                        if (markerIndex !== -1) {
                            device = searchData[markerIndex];
                        }
                        // Fallback: Search for device based on coordinates
                        if (!device) {
                            device = searchData.find(d => Math.abs(d.lat - newLat) < 0.0001 && Math.abs(d.lon - newLon) < 0.0001);
                        }
                        if (device) {
                            console.log('DragEnd: Device found:', device.address, device.type, newLat, newLon);
                            updateDeviceLocation(device.address, device.type, newLat, newLon);
                            console.log('pendingUpdates:', pendingUpdates.length);
                        } else {
                            console.warn('DragEnd: No device found for marker!', newLat, newLon);
                        }
                    });

                    // Middle-click handler for highlight function - Improved version
                    layer.on('mousedown', function(e) {
                        console.log('Mouse button pressed:', e.originalEvent.button);

                        // Check for middle mouse button (Button 1) or right-click as alternative
                        if (e.originalEvent.button === 1 || e.originalEvent.button === 2) {
                            e.originalEvent.preventDefault(); // Prevent default behavior
                            e.originalEvent.stopPropagation();

                            console.log('Middle/Right click detected on marker');

                            // Extract device info from popup
                            var popupContent = e.target.getPopup().getContent();
                            var addressMatch = popupContent.match(/(?:BSSID|MAC Address):<\/b>\s*([A-Fa-f0-9:]{17})/);
                            var ssidMatch = popupContent.match(/<b>(?:SSID|Device Name):<\/b>\s*([^<]+)/);
                            
                            console.log('Popup content:', popupContent);
                            console.log('Address match:', addressMatch);
                            console.log('SSID match:', ssidMatch);
                            
                            if (addressMatch && ssidMatch) {
                                var deviceAddress = addressMatch[1];
                                var deviceName = ssidMatch[1].trim();
                                
                                console.log('Found device:', deviceAddress, deviceName);
                                
                                // Find the device in the searchData
                                var device = searchData.find(d => d.address === deviceAddress);
                                if (device) {
                                    console.log('Device found in searchData, highlighting...');

                                    // Highlight the device like in search
                                    highlightDeviceByObject(device, deviceName);

                                    // Set SSID in search bar (clean special characters)
                                    var searchInput = document.getElementById('searchInput');
                                    if (searchInput && device.type === 'WIFI') {
                                        // Remove HTML entities and clean string
                                        var cleanName = deviceName.replace(/&lt;/g, '<')
                                                                  .replace(/&gt;/g, '>')
                                                                  .replace(/&amp;/g, '&')
                                                                  .replace(/&quot;/g, '"')
                                                                  .replace(/&#x27;/g, "'");

                                        searchInput.value = cleanName;

                                        // Trigger search update
                                        var event = new Event('input', { bubbles: true });
                                        searchInput.dispatchEvent(event);

                                        // Visual feedback
                                        searchInput.style.backgroundColor = '#e8f5e8';
                                        setTimeout(() => {
                                            searchInput.style.backgroundColor = '';
                                        }, 1000);
                                    }

                                    // Success feedback
                                    console.log('✅ Marker highlighted successfully!');
                                } else {
                                    console.warn('Device not found in searchData');
                                }
                            } else {
                                console.warn('Could not extract device info from popup');
                            }


                            return false; // Prevent further event propagation
                        }
                    });

                    // Additional context menu handler as alternative
                    layer.on('contextmenu', function(e) {
                        e.originalEvent.preventDefault();
                        console.log('Context menu (right click) on marker');

                        // Trigger the same highlight process
                        var popupContent = e.target.getPopup().getContent();
                        var addressMatch = popupContent.match(/(?:BSSID|MAC Address):<\/b>\s*([A-Fa-f0-9:]{17})/);
                        var ssidMatch = popupContent.match(/<b>(?:SSID|Device Name):<\/b>\s*([^<]+)/);
                        
                        if (addressMatch && ssidMatch) {
                            var deviceAddress = addressMatch[1];
                            var deviceName = ssidMatch[1].trim();
                            var device = searchData.find(d => d.address === deviceAddress);


                            if (device) {
                                highlightDeviceByObject(device, 'Right click');

                                var searchInput = document.getElementById('searchInput');
                                if (searchInput && device.type === 'WIFI') {
                                    var cleanName = deviceName.replace(/&lt;/g, '<')
                                                              .replace(/&gt;/g, '>')
                                                              .replace(/&amp;/g, '&')
                                                              .replace(/&quot;/g, '"')
                                                              .replace(/&#x27;/g, "'");
                                    searchInput.value = cleanName;

                                    var event = new Event('input', { bubbles: true });
                                    searchInput.dispatchEvent(event);
                                }

                                // Show confirmation
                                alert(`✅ Marker highlighted!\nSSID: ${deviceName}\nBSSID: ${deviceAddress}`);
                            }
                        }
                        
                        return false;
                    });
                }
            });
            console.log('Drag and click handlers setup complete');
        }, 2000);
    }

    // Setup search function
    function setupSearch() {
        const searchInput = document.getElementById('searchInput');
        const searchResults = document.getElementById('searchResults');
        const searchCount = document.getElementById('searchCount');

        searchInput.addEventListener('input', function() {
            const query = this.value.trim().toLowerCase();

            if (query.length < 2) {
                searchResults.style.display = 'none';
                searchCount.textContent = '';
                clearHighlights();
                return;
            }

            // Search in data: SSID OR MAC address
            const results = searchData.filter(device =>
                (device.name.toLowerCase().includes(query) || device.address.toLowerCase().includes(query)) && device.type === 'WIFI'
            );

            // Display results
            displaySearchResults(results, query);
        });

        // When clearing search bar, also remove highlights
        searchInput.addEventListener('keyup', function(e) {
            if (e.key === 'Escape' || (e.key === 'Backspace' && this.value.length === 0)) {
                clearHighlights();
                searchResults.style.display = 'none';
                searchCount.textContent = '';
            }
        });

        // Click outside closes results
        document.addEventListener('click', function(e) {
            if (!e.target.closest('.search-container')) {
                searchResults.style.display = 'none';
            }
        });
    }

    // Display search results
    function displaySearchResults(results, query) {
        const searchResults = document.getElementById('searchResults');
        const searchCount = document.getElementById('searchCount');

        if (results.length === 0) {
            searchResults.innerHTML = '<div class="search-result-item">No results found</div>';
            searchCount.textContent = '0 Results';
            clearHighlights(); // Clear highlighting when nothing found
            searchResults.style.display = 'block';
        } else {
            searchResults.innerHTML = results.map((device, index) => {
                const signalText = device.signal >= -50 ? 'Very strong' :
                                 device.signal >= -70 ? 'Strong' :
                                 device.signal >= -80 ? 'Medium' : 'Weak';
                
                return `<div class="search-result-item" onclick="highlightDevice(${index}, '${query}')">
                    <strong>${device.name}</strong><br>
                    <small>${device.address} | ${device.signal} dBm (${signalText}) | ${device.encryption}</small>
                </div>`;
            }).join('');

            searchCount.textContent = `${results.length} Result${results.length !== 1 ? 's' : ''}`;
            searchResults.style.display = 'block';

            // Automatically highlight if only one result was found
            if (results.length === 1) {
                setTimeout(() => {
                    highlightDeviceByObject(results[0], query);
                }, 100);
            }
        }

        // Save current search results
        window.currentSearchResults = results;
    }

    // Highlight device on map (via index from search results)
    function highlightDevice(index, query) {
        if (!map || !window.currentSearchResults) return;

        const device = window.currentSearchResults[index];
        highlightDeviceByObject(device, query);
    }

    // Highlight device on map (directly via device object)
    function highlightDeviceByObject(device, query) {
        if (!map || !device) return;

        // Check if the same device is already highlighted (toggle function)
        if (currentlyHighlightedDevice &&
            currentlyHighlightedDevice.address === device.address &&
            currentlyHighlightedDevice.type === device.type) {

            // Remove highlighting (Toggle OFF)
            clearHighlights();
            currentlyHighlightedDevice = null;

            // User feedback
            console.log(`🔄 Highlighting for ${device.name} (${device.address}) removed`);

            // Optional: Show brief message
            const searchInput = document.getElementById('searchInput');
            if (searchInput) {
                const originalPlaceholder = searchInput.placeholder;
                searchInput.placeholder = `✅ ${device.name} deselected`;
                setTimeout(() => {
                    searchInput.placeholder = originalPlaceholder;
                }, 2000);
            }

            return;
        }

        // Remove old highlights
        clearHighlights();

        // Save currently highlighted device
        currentlyHighlightedDevice = {
            address: device.address,
            type: device.type,
            name: device.name
        };

        // Zoom to position
        map.setView([device.lat, device.lon], 18);

        // Find the original marker for this device
        let originalMarkerIndex = -1;
        for (let i = 0; i < searchData.length; i++) {
            if (searchData[i].address === device.address && searchData[i].type === device.type) {
                originalMarkerIndex = i;
                break;
            }
        }

        // Ensure the original marker is visible (even if filters hid it)
        if (originalMarkerIndex !== -1) {
            const originalMarker = allMarkers[originalMarkerIndex];
            const originalCircle = allCircles[originalMarkerIndex];

            if (originalMarker && !originalMarker._map) {
                originalMarker.addTo(map);
            }
            if (originalCircle && !originalCircle._map) {
                originalCircle.addTo(map);
            }
        }

        // Additional golden highlight ring (without new marker)
        const highlightCircle = L.circle([device.lat, device.lon], {
            color: 'gold',
            fillColor: 'gold',
            fillOpacity: 0.3,
            radius: 80,
            weight: 4
        }).addTo(map);

        // Pulsing outer ring
        const pulseCircle = L.circle([device.lat, device.lon], {
            color: 'orange',
            fillColor: 'orange',
            fillOpacity: 0.1,
            radius: 120,
            weight: 2
        }).addTo(map);

        // 50m radius for Bluetooth search (only for WiFi devices)
        let searchRadius = null;
        let bluetoothMarkers = [];

        if (device.type === 'WIFI') {
            searchRadius = L.circle([device.lat, device.lon], {
                color: 'orange',
                fillColor: 'orange',
                fillOpacity: 0.1,
                radius: 50,
                weight: 2,
                dashArray: '5, 5'
            }).addTo(map);

            // Find and show all Bluetooth devices within 50m radius
            const nearbyBluetoothDevices = findBluetoothDevicesInRadius(device.lat, device.lon, 50);
            bluetoothMarkers = showNearbyBluetoothDevices(nearbyBluetoothDevices);

            // Show info about found Bluetooth devices (delayed)
            if (nearbyBluetoothDevices.length > 0 && bluetoothMode !== 'none') {
                setTimeout(() => {
                    let displayedCount = bluetoothMarkers.length / 3; // 3 elements per device (Marker, Circle, Label)
                    let modeText = bluetoothMode === 'known' ? ' (known only)' :
                                  bluetoothMode === 'all' ? ' (all)' : '';
                    console.log(`📱 ${displayedCount} of ${nearbyBluetoothDevices.length} Bluetooth devices displayed within 50m radius${modeText}`);
                }, 1000);
            }
        }

        // Save highlights for later removal
        highlightedMarkers.push(highlightCircle, pulseCircle);
        if (searchRadius) highlightedMarkers.push(searchRadius);
        highlightedMarkers.push(...bluetoothMarkers);

        // Hide search results if open
        document.getElementById('searchResults').style.display = 'none';

        // Animation for attention
        setTimeout(() => {
            if (highlightCircle._map) {
                highlightCircle.setStyle({fillOpacity: 0.1});
                setTimeout(() => {
                    if (highlightCircle._map) highlightCircle.setStyle({fillOpacity: 0.3});
                }, 500);
            }
        }, 300);
    }

    // Remove highlights
    function clearHighlights() {
        highlightedMarkers.forEach(marker => {
            if (marker._map) {
                map.removeLayer(marker);
            }
        });
        highlightedMarkers = [];
        // Reset currently highlighted device when highlights are manually cleared
        if (currentlyHighlightedDevice) {
            console.log(`🔄 Highlighting for ${currentlyHighlightedDevice.name} manually removed`);
            currentlyHighlightedDevice = null;
        }
    }

    function debugMarkers() {
        let visibleCount = 0;
        let hiddenCount = 0;
        let highlightedCount = 0;
        
        allMarkers.forEach(marker => {
            if (mymap.hasLayer(marker)) {
                visibleCount++;
            } else {
                hiddenCount++;
            }
        });


        highlightedCount = highlightedMarkers.length;

        let debugInfo = `Debug Info:\\n`;
        debugInfo += `Visible Markers: ${visibleCount}\\n`;
        debugInfo += `Hidden Markers: ${hiddenCount}\\n`;
        debugInfo += `Highlighted Markers: ${highlightedCount}\\n`;
        debugInfo += `Total Markers: ${allMarkers.length}\\n`;
        debugInfo += `Filter Status: Signal=${signalFilter.enabled}, Vendor=${vendorFilter.enabled}, Security=${securityFilter.enabled}`;
        
        alert(debugInfo);
        console.log('Marker Debug:', {
            visible: visibleCount,
            hidden: hiddenCount,
            highlighted: highlightedCount,
            total: allMarkers.length,
            filters: {
                signal: signalFilter.enabled,
                vendor: vendorFilter.enabled,
                security: securityFilter.enabled
            }
        });
    }

    // Calculate distance between two GPS coordinates (Haversine formula)
    function calculateDistance(lat1, lon1, lat2, lon2) {
        const R = 6371e3; // Earth radius in meters
        const φ1 = lat1 * Math.PI/180;
        const φ2 = lat2 * Math.PI/180;
        const Δφ = (lat2-lat1) * Math.PI/180;
        const Δλ = (lon2-lon1) * Math.PI/180;

        const a = Math.sin(Δφ/2) * Math.sin(Δφ/2) +
                  Math.cos(φ1) * Math.cos(φ2) *
                  Math.sin(Δλ/2) * Math.sin(Δλ/2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return R * c; // Distance in meters
    }

    // Find all Bluetooth devices within specified radius
    function findBluetoothDevicesInRadius(centerLat, centerLon, radiusMeters) {
        return searchData.filter(device => {
            if (device.type !== 'BLUETOOTH') return false;

            const distance = calculateDistance(centerLat, centerLon, device.lat, device.lon);
            return distance <= radiusMeters;
        });
    }

    // Show nearby Bluetooth devices
    function showNearbyBluetoothDevices(bluetoothDevices) {
        const bluetoothMarkers = [];

        // Check Bluetooth mode and filter accordingly
        let filteredDevices = bluetoothDevices;

        if (bluetoothMode === 'none') {
            // Don't show any Bluetooth devices
            return bluetoothMarkers;
        } else if (bluetoothMode === 'known') {
            // Only known Bluetooth devices (without "Unknown")
            filteredDevices = bluetoothDevices.filter(device => {
                const deviceName = device.name || '';
                return deviceName !== '[Unknown Device]' &&
                       deviceName !== 'Unknown Device' &&
                       deviceName !== '' &&
                       deviceName !== null &&
                       !deviceName.toLowerCase().includes('unknown');
            });
        }
        // For bluetoothMode === 'all', all devices are shown (no filtering)

        filteredDevices.forEach((device, index) => {
            // Bluetooth marker with special color
            const btMarker = L.marker([device.lat, device.lon], {
                icon: L.icon({
                    iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-violet.png',
                    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
                    iconSize: [20, 33],
                    iconAnchor: [10, 33],
                    popupAnchor: [1, -28],
                    shadowSize: [33, 33]
                })
            }).addTo(map);

            // Popup for Bluetooth device
            const signalText = device.signal >= -50 ? 'Very strong' :
                             device.signal >= -70 ? 'Strong' :
                             device.signal >= -80 ? 'Medium' : 'Weak';

            btMarker.bindPopup(`
                <b>📱 BLUETOOTH: ${device.name}</b><br>
                <b>MAC:</b> ${device.address}<br>
                <b>Signal:</b> ${device.signal} dBm (${signalText})<br>
                <b>Class:</b> ${device.encryption}<br>
                <small><i>Automatically displayed (50m radius)</i></small>
            `);

            // Name label above Bluetooth marker
            const nameLabel = L.marker([device.lat, device.lon], {
                icon: L.divIcon({
                    className: 'bt-name-label',
                    html: `<div style="
                        background: rgba(128, 0, 128, 0.9);
                        color: white;
                        padding: 2px 6px;
                        border-radius: 3px;
                        font-size: 11px;
                        font-weight: bold;
                        white-space: nowrap;
                        text-align: center;
                        border: 1px solid purple;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.3);
                        transform: translateY(-45px);
                        pointer-events: none;
                    ">${device.name || '[Unknown BT]'}</div>`,
                    iconSize: [0, 0],
                    iconAnchor: [0, 0]
                })
            }).addTo(map);

            // Small circle around Bluetooth device
            const btCircle = L.circle([device.lat, device.lon], {
                color: 'purple',
                fillColor: 'purple',
                fillOpacity: 0.2,
                radius: 20,
                weight: 2
            }).addTo(map);

            bluetoothMarkers.push(btMarker, btCircle, nameLabel);
        });

        return bluetoothMarkers;
    }

    // Function to update device position in database
    function updateDeviceLocation(deviceAddress, deviceType, newLat, newLon) {
        // Show confirmation with new coordinates
        const confirmed = confirm(
            `Move device ${deviceAddress} (${deviceType})?\n\n` +
            `New coordinates:\n` +
            `Latitude: ${newLat.toFixed(6)}\n` +
            `Longitude: ${newLon.toFixed(6)}\n\n` +
            `Change will be queued for database update.`
        );

        if (!confirmed) {
            // Reload page to reset marker
            location.reload();
            return;
        }

        // Add update to list
        pendingUpdates.push({
            address: deviceAddress,
            type: deviceType,
            latitude: newLat,
            longitude: newLon,
            timestamp: new Date().toISOString()
        });

        // Show success message with batch option
        const batchUpdate = pendingUpdates.length > 1;
        let message = `📍 Position queued!\n\n` +
            `Device: ${deviceAddress}\n` +
            `Type: ${deviceType}\n` +
            `New Lat: ${newLat.toFixed(6)}\n` +
            `New Lon: ${newLon.toFixed(6)}\n\n`;

        if (batchUpdate) {
            message += `📝 ${pendingUpdates.length} changes queued.\n` +
                      `When closing the application, all\n` +
                      `changes will be written to the database.`;
        } else {
            message += `💾 When closing the application, the\n` +
                      `change will be written to the database.`;
        }

        alert(message);

        // Update also in searchData for consistency
        const dataIndex = searchData.findIndex(d => d.address === deviceAddress && d.type === deviceType);
        if (dataIndex !== -1) {
            searchData[dataIndex].lat = newLat;
            searchData[dataIndex].lon = newLon;
        }

        console.log(`Position queued for update: ${deviceAddress} -> ${newLat}, ${newLon}`);
        console.log(`Total pending updates: ${pendingUpdates.length}`);
    }

    // Save updates when closing the page
    window.addEventListener('beforeunload', function(e) {
        if (pendingUpdates.length > 0) {
            // Save updates in localStorage for Python processing
            localStorage.setItem('pendingLocationUpdates', JSON.stringify(pendingUpdates));

            const message = `${pendingUpdates.length} position change(s) will be saved...`;
            e.returnValue = message;
            return message;
        }
    });

    // Function to reset all markers to original positions
    function resetAllMarkers() {
        if (!confirm('Reset all markers to original positions?')) return;

        // Reload page to reset all markers
        location.reload();
    }

    // Function to save all queued position changes
    function saveAllMarkers() {
        if (pendingUpdates.length === 0) {
            alert('No position changes queued!');
            return;
        }
        // Show overview of changed markers
        let msg = `The following markers have been moved and will be saved:\n\n`;
        pendingUpdates.forEach((upd, idx) => {
            msg += `${idx+1}. ${upd.address} (${upd.type})\n   New Lat: ${upd.latitude.toFixed(6)}\n   New Lon: ${upd.longitude.toFixed(6)}\n\n`;
        });
        msg += `\nReally write to database now?`;
        if (!confirm(msg)) return;

        // Save updates in localStorage and as file
        localStorage.setItem('pendingLocationUpdates', JSON.stringify(pendingUpdates));
        const updatesJson = JSON.stringify(pendingUpdates, null, 2);
        const blob = new Blob([updatesJson], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'wifi_scanner_updates.json';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);

        // Show status
        const statusEl = document.getElementById('save-status');
        statusEl.innerHTML = `✅ ${pendingUpdates.length} position(s) exported!`;
        statusEl.style.color = 'green';

        setTimeout(() => {
            alert(`✅ Updates have been downloaded as JSON file!\n\n` +
                  `Place the file "wifi_scanner_updates.json" in the program folder.\n\n` +
                  `The updates will be automatically written to the database at the next program start.`);
            pendingUpdates = [];
            statusEl.innerHTML = '';
        }, 1500);
    }
    </script>
    """
    
    device_map.get_root().html.add_child(folium.Element(search_and_control_js))

    # Add legend
    total_wifi = wifi_open + wifi_encrypted
    total_devices = len(device_data)

    legend_html = f"""
    <div id="legend-info-box" style="position: fixed;
                top: 10px; right: 10px; width: 230px; max-height: 340px;
                background-color: white; border:1.5px solid #888; z-index:9999;
                font-size:12px; padding: 7px 8px; overflow-y: auto; box-shadow: 0 1px 7px rgba(0,0,0,0.13); border-radius: 7px;">
    <h4 style='margin:0 0 6px 0; font-size:13px; font-weight:bold;'>WiFi & Bluetooth Scanner</h4>
    <p style='margin:2px 0;'><i class="fa fa-wifi" style="color:red"></i> WiFi Open: {wifi_open}</p>
    <p style='margin:2px 0;'><i class="fa fa-lock" style="color:green"></i> WiFi Encrypted: {wifi_encrypted}</p>
    <p style='margin:2px 0;'><i class="fa fa-bluetooth" style="color:blue"></i> Bluetooth: {bluetooth_count}</p>
    <p style='margin:2px 0;'><i class="fa fa-bluetooth" style="color:darkblue"></i> Bluetooth without Unknown: {bluetooth_known_count}</p>
    <p style='margin:2px 0;'><i class="fa fa-wifi" style="color:orange"></i> Vodafone Homespot/Hotspot: {vodafone_homespot_count}</p>
    <p style='margin:2px 0;'><i class="fa fa-star" style="color:purple"></i> WiFi Open without Vodafone: {wifi_open_no_vodafone}</p>
    <p style='margin:2px 0;'><b>Total:</b> {total_devices} Devices</p>
    <hr style='margin:6px 0;'>
    <div id="legend-selected-device" style="margin-bottom:6px; font-size:11px;"></div>
    <button id="legend-highlight-btn" style="display:none;margin-bottom:6px;padding:4px 8px;font-size:11px;background:#ffd700;color:#333;border:none;border-radius:3px;cursor:pointer;">Highlight</button>
    <p style='margin:2px 0 0 0;'><small>DB: {db_filename}</small></p>
    </div>
    """
    device_map.get_root().html.add_child(folium.Element(legend_html))
    
    # --- Add JS for legend highlight button and marker click integration ---
    legend_highlight_js = """
    <script>
    let legendSelectedDevice = null;
    let legendSelectedDeviceIndex = null;
    // Wait for map and markers to be ready
    function setupLegendHighlightButton() {
        // Find allMarkers and map after they are set up
        setTimeout(function() {
            // Attach click event to all markers
            if (typeof allMarkers !== 'undefined' && allMarkers.length > 0) {
                allMarkers.forEach((marker, idx) => {
                    marker.on('click', function(e) {
                        // Get device info
                        const device = searchData[idx];
                        legendSelectedDevice = device;
                        legendSelectedDeviceIndex = idx;
                        // Show info in legend
                        let info = '';
                        if (device.type === 'WIFI') {
                            info += `<b>SSID:</b> ${device.name}<br>`;
                            info += `<b>BSSID:</b> ${device.address}<br>`;
                        } else {
                            info += `<b>Name:</b> ${device.name}<br>`;
                            info += `<b>MAC:</b> ${device.address}<br>`;
                        }
                        info += `<b>Type:</b> ${device.type}<br>`;
                        info += `<b>Signal:</b> ${device.signal} dBm<br>`;
                        document.getElementById('legend-selected-device').innerHTML = info;
                        // Show highlight button
                        document.getElementById('legend-highlight-btn').style.display = 'block';

                        // --- NEW: Display movement data ---
                        if (movementLayer) {
                            map.removeLayer(movementLayer);
                            movementLayer = null;
                        }

                        if (device.last_seen_lat && device.last_seen_lon) {
                            movementLayer = L.layerGroup().addTo(map);

                            // Marker for last position
                            const lastSeenMarker = L.marker([device.last_seen_lat, device.last_seen_lon], {
                                icon: L.icon({
                                    iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-grey.png',
                                    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
                                    iconSize: [25, 41],
                                    iconAnchor: [12, 41],
                                    popupAnchor: [1, -34],
                                    shadowSize: [41, 41]
                                })
                            }).addTo(movementLayer);
                            lastSeenMarker.bindPopup(`<b>Last Position of:</b><br>${device.name}<br>${device.last_seen_timestamp}`);

                            // Line between the two points
                            const line = L.polyline([[device.lat, device.lon], [device.last_seen_lat, device.last_seen_lon]], {
                                color: 'black',
                                weight: 2,
                                opacity: 0.7,
                                dashArray: '5, 10'
                            }).addTo(movementLayer);
                        }
                    });
                });
            }
            // Highlight button click
            const btn = document.getElementById('legend-highlight-btn');
            if (btn) {
                btn.onclick = function() {
                    if (legendSelectedDevice && typeof highlightDeviceByObject === 'function') {
                        highlightDeviceByObject(legendSelectedDevice, 'Legend Highlight');
                    }
                };
            }
        }, 2000);
    }
    // Run after DOM ready
    document.addEventListener('DOMContentLoaded', setupLegendHighlightButton);
    </script>
    """
    device_map.get_root().html.add_child(folium.Element(legend_highlight_js))
    return device_map

def process_pending_location_updates(db_path, passphrase=None):
    """Processes pending location updates from temporary file (with encryption support)"""
    import json, os, tempfile
    try:
        print("Checking for pending location updates...")
        # Support both storage locations
        update_files = [
            os.path.join(os.path.dirname(db_path), 'wifi_scanner_updates.json'),
            os.path.join(tempfile.gettempdir(), 'wifi_scanner_updates.json')
        ]
        found_file = None
        for fpath in update_files:
            if os.path.exists(fpath):
                found_file = fpath
                break
        if not found_file:
            print("No position updates found.")
            return True
        with open(found_file, 'r', encoding='utf-8') as f:
            pending_updates = json.load(f)
        if not pending_updates:
            os.remove(found_file)
            print("Update file is empty.")
            return True
        print(f"Found: {len(pending_updates)} pending position changes")
        successful_updates = 0
        for update in pending_updates:
            try:
                success = update_device_location_in_db(
                    db_path,
                    update['address'],
                    update['type'],
                    update['latitude'],
                    update['longitude'],
                    passphrase  # Pass passphrase for encrypted databases
                )
                if success:
                    successful_updates += 1
                    print("✅ Updated device location.")
                else:
                    print("❌ Failed to update device location.")
            except Exception as e:
                print(f"❌ Error updating device location: {str(e)}")
        print(f"Successfully updated: {successful_updates}/{len(pending_updates)} devices")
        os.remove(found_file)
        if successful_updates > 0:
            print("Position changes have been applied to the database!")
        return True
    except Exception as e:
        print(f"Error processing updates: {str(e)}")
        return False

def main():
    """Main function of the program"""
    print("WiFi & Bluetooth Scanner Map Viewer")
    print("=" * 40)

    # Select database file
    db_path = select_database_file()

    if not db_path:
        print("No file selected. Program will exit.")
        return

    print(f"Loading database: {os.path.basename(db_path)}")
    
    # Check if database is encrypted and get passphrase
    passphrase = None
    if ENCRYPTION_SUPPORT:
        db_enc = DatabaseEncryption(db_path)
        if db_enc.is_database_encrypted():
            print("🔒 Database is encrypted")
            
            root = tk.Tk()
            root.withdraw()
            
            passphrase = simpledialog.askstring(
                "Database Encrypted",
                "This database is encrypted.\nPlease enter your passphrase:",
                show='*'
            )
            
            root.destroy()
            
            if not passphrase:
                print("No passphrase provided. Cannot open encrypted database.")
                messagebox.showerror("Error", "No passphrase provided!")
                return
            
            # Test passphrase
            if not db_enc.connect_encrypted(passphrase):
                print("Wrong passphrase!")
                messagebox.showerror("Error", "Wrong passphrase or corrupted database!")
                return
            db_enc.close()
            print("✓ Passphrase verified successfully")

    # Process pending location updates (with passphrase if needed)
    if passphrase:
        # Need to update process_pending_location_updates to accept passphrase
        process_pending_location_updates(db_path, passphrase)
    else:
        process_pending_location_updates(db_path)

    # Load device data (WiFi + Bluetooth) with passphrase
    device_data = load_wifi_data(db_path, passphrase)

    if not device_data:
        print("No valid device data found.")
        return

    # Count different device types
    wifi_count = sum(1 for device in device_data if device['type'] == 'WIFI')
    bluetooth_count = sum(1 for device in device_data if device['type'] == 'BLUETOOTH')

    print(f"Found: {len(device_data)} devices with GPS data")
    print(f"  - WiFi networks: {wifi_count}")
    print(f"  - Bluetooth devices: {bluetooth_count}")

    # Show vendor statistics
    vendors = {}
    for device in device_data:
        vendor = device.get('vendor', 'Unknown')
        if vendor != 'Unknown' and not vendor.startswith('Unknown ('):
            vendors[vendor] = vendors.get(vendor, 0) + 1

    if vendors:
        print(f"\nTop Manufacturers:")
        sorted_vendors = sorted(vendors.items(), key=lambda x: x[1], reverse=True)[:5]
        for vendor, count in sorted_vendors:
            print(f"  - {vendor}: {count} devices")

    # Create map
    db_filename = os.path.basename(db_path)
    device_map = create_wifi_map(device_data, db_filename, db_path)

    if device_map:
        # Create temporary HTML file
        temp_file = tempfile.NamedTemporaryFile(delete=False, suffix='.html')
        map_path = temp_file.name
        temp_file.close()

        # Save map
        device_map.save(map_path)

        print(f"Map has been created: {map_path}")
        print("Opening map in browser...")

        # Open map in browser
        webbrowser.open('file://' + os.path.realpath(map_path))

        input("Press Enter to exit...")

        # Delete temporary file
        try:
            os.unlink(map_path)
        except:
            pass

if __name__ == "__main__":
    main()

