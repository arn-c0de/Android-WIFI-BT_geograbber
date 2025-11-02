#!/usr/bin/env python3
"""
Database Migration Script
Converts old German database format to new English format compatible with the app
"""

import sqlite3
import sys
import os
from datetime import datetime

# Paths
OLD_DB = r"C:\Users\644aa\Downloads\MainScan-31.08.25.db"
NEW_DB = r"C:\Users\644aa\Downloads\MainScan-31.08.25_MIGRATED.db"

def translate_encryption(german_encryption):
    """Translate German encryption values to English"""
    if not german_encryption:
        return "open"
    
    german_encryption = german_encryption.lower()
    
    if "offen" in german_encryption or "open" in german_encryption:
        return "open"
    elif "verschlüsselt" in german_encryption or "encrypted" in german_encryption:
        return "encrypted"
    else:
        # Default: if it contains security info, it's encrypted
        return "encrypted" if german_encryption else "open"

def create_new_database(conn):
    """Create tables in the new database with correct English schema"""
    cursor = conn.cursor()
    
    # Create wifi_data table (English schema)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS wifi_data (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ssid TEXT,
            bssid TEXT,
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
            latitude REAL,
            longitude REAL,
            timestamp INTEGER,
            last_seen_latitude REAL,
            last_seen_longitude REAL,
            last_seen_timestamp INTEGER,
            movement_distance REAL
        )
    """)
    
    # Create device_data table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS device_data (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_name TEXT,
            device_address TEXT,
            device_type TEXT,
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
            latitude REAL,
            longitude REAL,
            timestamp INTEGER,
            last_seen_latitude REAL,
            last_seen_longitude REAL,
            last_seen_timestamp INTEGER,
            movement_distance REAL
        )
    """)
    
    # Create android_metadata table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS android_metadata (
            locale TEXT
        )
    """)
    cursor.execute("INSERT INTO android_metadata (locale) VALUES ('en_US')")
    
    conn.commit()
    print("✓ New database schema created")

def migrate_wifi_data(old_conn, new_conn):
    """Migrate wifi_data from old DB to new DB"""
    old_cursor = old_conn.cursor()
    new_cursor = new_conn.cursor()
    
    print("\nMigrating wifi_data...")
    
    # Get all wifi data from old DB
    old_cursor.execute("SELECT * FROM wifi_data")
    rows = old_cursor.fetchall()
    
    # Get column names
    old_cursor.execute("PRAGMA table_info(wifi_data)")
    columns = [col[1] for col in old_cursor.fetchall()]
    
    migrated = 0
    errors = 0
    
    for row in rows:
        try:
            # Create dict from row
            data = dict(zip(columns, row))
            
            # Translate encryption
            encryption = translate_encryption(data.get('verschluesselung'))
            
            # Insert into new DB (without id to auto-generate)
            new_cursor.execute("""
                INSERT INTO wifi_data (
                    ssid, bssid, signal_strength, encryption, frequency, channel,
                    capabilities, wifi_standard, vendor_info, channel_width,
                    center_freq0, center_freq1, max_connection_speed,
                    latitude, longitude, timestamp,
                    last_seen_latitude, last_seen_longitude, last_seen_timestamp, movement_distance
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                data.get('ssid'),
                data.get('bssid'),
                data.get('signal_strength'),
                encryption,
                data.get('frequency'),
                data.get('channel'),
                data.get('capabilities'),
                data.get('wifi_standard'),
                data.get('vendor_info'),
                data.get('channel_width'),
                data.get('center_freq0'),
                data.get('center_freq1'),
                data.get('max_connection_speed'),
                data.get('latitude'),
                data.get('longitude'),
                data.get('timestamp'),
                data.get('last_seen_latitude'),
                data.get('last_seen_longitude'),
                data.get('last_seen_timestamp'),
                data.get('movement_distance')
            ))
            migrated += 1
            
            if migrated % 500 == 0:
                print(f"  Migrated {migrated} WiFi records...")
                new_conn.commit()
                
        except Exception as e:
            errors += 1
            print(f"  Error migrating WiFi row {data.get('id', '?')}: {e}")
    
    new_conn.commit()
    print(f"✓ WiFi data migration complete: {migrated} records migrated, {errors} errors")
    return migrated, errors

def migrate_device_data(old_conn, new_conn):
    """Migrate device_data from old DB to new DB"""
    old_cursor = old_conn.cursor()
    new_cursor = new_conn.cursor()
    
    print("\nMigrating device_data...")
    
    # Get all device data from old DB
    old_cursor.execute("SELECT * FROM device_data")
    rows = old_cursor.fetchall()
    
    # Get column names
    old_cursor.execute("PRAGMA table_info(device_data)")
    columns = [col[1] for col in old_cursor.fetchall()]
    
    migrated = 0
    errors = 0
    
    for row in rows:
        try:
            # Create dict from row
            data = dict(zip(columns, row))
            
            # Insert into new DB (without id to auto-generate)
            new_cursor.execute("""
                INSERT INTO device_data (
                    device_name, device_address, device_type, signal_strength, encryption_info,
                    frequency, channel, channel_width, capabilities,
                    center_freq0, center_freq1, wifi_standard, vendor_info,
                    is_passpoint_network, operator_friendly_name, venue_name, max_connection_speed,
                    latitude, longitude, timestamp,
                    last_seen_latitude, last_seen_longitude, last_seen_timestamp, movement_distance
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                data.get('device_name'),
                data.get('device_address'),
                data.get('device_type'),
                data.get('signal_strength'),
                data.get('encryption_info'),
                data.get('frequency'),
                data.get('channel'),
                data.get('channel_width'),
                data.get('capabilities'),
                data.get('center_freq0'),
                data.get('center_freq1'),
                data.get('wifi_standard'),
                data.get('vendor_info'),
                data.get('is_passpoint_network'),
                data.get('operator_friendly_name'),
                data.get('venue_name'),
                data.get('max_connection_speed'),
                data.get('latitude'),
                data.get('longitude'),
                data.get('timestamp'),
                data.get('last_seen_latitude'),
                data.get('last_seen_longitude'),
                data.get('last_seen_timestamp'),
                data.get('movement_distance')
            ))
            migrated += 1
            
            if migrated % 500 == 0:
                print(f"  Migrated {migrated} device records...")
                new_conn.commit()
                
        except Exception as e:
            errors += 1
            print(f"  Error migrating device row {data.get('id', '?')}: {e}")
    
    new_conn.commit()
    print(f"✓ Device data migration complete: {migrated} records migrated, {errors} errors")
    return migrated, errors

def main():
    print("=" * 70)
    print("DATABASE MIGRATION TOOL")
    print("=" * 70)
    print(f"\nSource DB: {OLD_DB}")
    print(f"Target DB: {NEW_DB}")
    print(f"\nStarted: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("-" * 70)
    
    # Check if old DB exists
    if not os.path.exists(OLD_DB):
        print(f"❌ Error: Source database not found: {OLD_DB}")
        sys.exit(1)
    
    # Check if new DB already exists
    if os.path.exists(NEW_DB):
        response = input(f"\n⚠️  Target database already exists. Overwrite? (yes/no): ")
        if response.lower() != 'yes':
            print("Migration cancelled.")
            sys.exit(0)
        os.remove(NEW_DB)
        print("✓ Old target database removed")
    
    try:
        # Connect to both databases
        old_conn = sqlite3.connect(OLD_DB)
        new_conn = sqlite3.connect(NEW_DB)
        
        print("\n✓ Connected to both databases")
        
        # Create new schema
        create_new_database(new_conn)
        
        # Migrate data
        wifi_migrated, wifi_errors = migrate_wifi_data(old_conn, new_conn)
        device_migrated, device_errors = migrate_device_data(old_conn, new_conn)
        
        # Close connections
        old_conn.close()
        new_conn.close()
        
        # Summary
        print("\n" + "=" * 70)
        print("MIGRATION SUMMARY")
        print("=" * 70)
        print(f"WiFi records:   {wifi_migrated} migrated, {wifi_errors} errors")
        print(f"Device records: {device_migrated} migrated, {device_errors} errors")
        print(f"Total:          {wifi_migrated + device_migrated} records")
        print(f"\nFinished: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"\n✓ Migration complete!")
        print(f"\nNew database saved to:\n{NEW_DB}")
        print("\nYou can now import this database into the Android app!")
        
    except Exception as e:
        print(f"\n❌ Error during migration: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
