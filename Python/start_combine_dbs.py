import sqlite3
import tkinter as tk
from tkinter import filedialog, messagebox, ttk
import os
import shutil
import math
from datetime import datetime

class DatabaseCombiner:
    def __init__(self):
        self.main_db_path = None
        self.source_db_paths = []
        self.setup_gui()
    
    def setup_gui(self):
        """Erstellt die GUI für die Datenbankzusammenführung"""
        self.root = tk.Tk()
        self.root.title("WiFi/Bluetooth Datenbank Combiner")
        self.root.geometry("800x600")
        self.root.resizable(True, True)
        
        # Hauptframe
        main_frame = ttk.Frame(self.root, padding="10")
        main_frame.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        # Titel
        title_label = ttk.Label(main_frame, text="Datenbank Zusammenführung", 
                               font=("Arial", 16, "bold"))
        title_label.grid(row=0, column=0, columnspan=3, pady=(0, 20))
        
        # Main Database Sektion
        ttk.Label(main_frame, text="Haupt-Datenbank (Ziel):", 
                 font=("Arial", 12, "bold")).grid(row=1, column=0, sticky=tk.W, pady=(0, 5))
        
        self.main_db_var = tk.StringVar()
        main_db_entry = ttk.Entry(main_frame, textvariable=self.main_db_var, width=60)
        main_db_entry.grid(row=2, column=0, columnspan=2, sticky=(tk.W, tk.E), pady=(0, 5))
        
        ttk.Button(main_frame, text="Auswählen", 
                  command=self.select_main_db).grid(row=2, column=2, padx=(5, 0), pady=(0, 5))
        
        # Source Databases Sektion
        ttk.Label(main_frame, text="Quell-Datenbanken (hinzufügen):", 
                 font=("Arial", 12, "bold")).grid(row=3, column=0, sticky=tk.W, pady=(20, 5))
        
        # Listbox für Quell-DBs
        self.source_listbox = tk.Listbox(main_frame, height=6, width=80)
        self.source_listbox.grid(row=4, column=0, columnspan=2, sticky=(tk.W, tk.E), pady=(0, 5))
        
        # Scrollbar für Listbox
        scrollbar = ttk.Scrollbar(main_frame, orient=tk.VERTICAL, command=self.source_listbox.yview)
        scrollbar.grid(row=4, column=2, sticky=(tk.N, tk.S), pady=(0, 5))
        self.source_listbox.config(yscrollcommand=scrollbar.set)
        
        # Buttons für Quell-DBs
        source_buttons_frame = ttk.Frame(main_frame)
        source_buttons_frame.grid(row=5, column=0, columnspan=3, pady=(5, 0))
        
        ttk.Button(source_buttons_frame, text="DB hinzufügen", 
                  command=self.add_source_db).pack(side=tk.LEFT, padx=(0, 5))
        ttk.Button(source_buttons_frame, text="Ausgewählte entfernen", 
                  command=self.remove_source_db).pack(side=tk.LEFT, padx=(0, 5))
        ttk.Button(source_buttons_frame, text="Alle entfernen", 
                  command=self.clear_source_dbs).pack(side=tk.LEFT)
        
        # Optionen
        options_frame = ttk.LabelFrame(main_frame, text="Zusammenführungs-Optionen", padding="10")
        options_frame.grid(row=6, column=0, columnspan=3, sticky=(tk.W, tk.E), pady=(20, 0))
        
        self.backup_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(options_frame, text="Backup der Haupt-DB erstellen", 
                       variable=self.backup_var).grid(row=0, column=0, sticky=tk.W, pady=(0, 5))
        
        self.signal_priority_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(options_frame, text="Bessere Signalstärke bevorzugen", 
                       variable=self.signal_priority_var).grid(row=1, column=0, sticky=tk.W, pady=(0, 5))
        
        self.coordinate_update_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(options_frame, text="Koordinaten aktualisieren bei besserem Signal", 
                       variable=self.coordinate_update_var).grid(row=2, column=0, sticky=tk.W, pady=(0, 5))
        
        # Progress Bar
        self.progress_var = tk.DoubleVar()
        self.progress_bar = ttk.Progressbar(main_frame, variable=self.progress_var, 
                                          maximum=100, length=400)
        self.progress_bar.grid(row=7, column=0, columnspan=3, sticky=(tk.W, tk.E), pady=(20, 5))
        
        # Status Label
        self.status_var = tk.StringVar(value="Bereit zum Start...")
        self.status_label = ttk.Label(main_frame, textvariable=self.status_var)
        self.status_label.grid(row=8, column=0, columnspan=3, pady=(0, 10))
        
        # Action Buttons
        action_frame = ttk.Frame(main_frame)
        action_frame.grid(row=9, column=0, columnspan=3, pady=(10, 0))
        
        ttk.Button(action_frame, text="🔍 Analyse starten", 
                  command=self.analyze_databases).pack(side=tk.LEFT, padx=(0, 10))
        ttk.Button(action_frame, text="🧹 DB bereinigen", 
                  command=self.clean_database).pack(side=tk.LEFT, padx=(0, 10))
        ttk.Button(action_frame, text="� Repair DB", 
                  command=self.repair_database).pack(side=tk.LEFT, padx=(0, 10))
        ttk.Button(action_frame, text="�🚀 Zusammenführen", 
                  command=self.combine_databases).pack(side=tk.LEFT, padx=(0, 10))
        ttk.Button(action_frame, text="❌ Beenden", 
                  command=self.root.quit).pack(side=tk.LEFT)
        
        # Log Text Area
        log_frame = ttk.LabelFrame(main_frame, text="Log", padding="5")
        log_frame.grid(row=10, column=0, columnspan=3, sticky=(tk.W, tk.E, tk.N, tk.S), pady=(20, 0))
        
        self.log_text = tk.Text(log_frame, height=8, width=80)
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
        """Fügt eine Nachricht zum Log hinzu"""
        timestamp = datetime.now().strftime("%H:%M:%S")
        self.log_text.insert(tk.END, f"[{timestamp}] {message}\n")
        self.log_text.see(tk.END)
        self.root.update()
    
    def select_main_db(self):
        """Wählt die Haupt-Datenbank aus"""
        initial_dir = os.path.dirname(os.path.abspath(__file__))
        file_path = filedialog.askopenfilename(
            title="Haupt-Datenbank auswählen",
            initialdir=initial_dir,
            filetypes=[("Database files", "*.db"), ("All files", "*.*")]
        )
        
        if file_path:
            self.main_db_path = file_path
            self.main_db_var.set(file_path)
            self.log(f"Haupt-DB ausgewählt: {os.path.basename(file_path)}")
    
    def add_source_db(self):
        """Fügt eine Quell-Datenbank hinzu"""
        initial_dir = os.path.dirname(os.path.abspath(__file__))
        file_paths = filedialog.askopenfilenames(
            title="Quell-Datenbank(en) auswählen",
            initialdir=initial_dir,
            filetypes=[("Database files", "*.db"), ("All files", "*.*")]
        )
        
        for file_path in file_paths:
            if file_path not in self.source_db_paths and file_path != self.main_db_path:
                self.source_db_paths.append(file_path)
                self.source_listbox.insert(tk.END, file_path)
                self.log(f"Quell-DB hinzugefügt: {os.path.basename(file_path)}")
            elif file_path == self.main_db_path:
                messagebox.showwarning("Warnung", "Die Haupt-DB kann nicht als Quell-DB verwendet werden!")
    
    def remove_source_db(self):
        """Entfernt ausgewählte Quell-Datenbank"""
        selection = self.source_listbox.curselection()
        if selection:
            index = selection[0]
            removed_path = self.source_db_paths.pop(index)
            self.source_listbox.delete(index)
            self.log(f"Quell-DB entfernt: {os.path.basename(removed_path)}")
    
    def clear_source_dbs(self):
        """Entfernt alle Quell-Datenbanken"""
        self.source_db_paths.clear()
        self.source_listbox.delete(0, tk.END)
        self.log("Alle Quell-DBs entfernt")
    
    def get_table_info(self, db_path):
        """Ermittelt Informationen über die Tabellen in der Datenbank"""
        try:
            conn = sqlite3.connect(db_path)
            cursor = conn.cursor()
            
            # Prüfe verfügbare Tabellen
            cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
            tables = [row[0] for row in cursor.fetchall()]
            
            info = {
                'has_device_data': 'device_data' in tables,
                'has_wifi_data': 'wifi_data' in tables,
                'tables': tables
            }
            
            # Zähle Einträge
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
            self.log(f"Fehler beim Analysieren von {os.path.basename(db_path)}: {str(e)}")
            return None
    
    def analyze_databases(self):
        """Analysiert alle ausgewählten Datenbanken"""
        if not self.main_db_path:
            messagebox.showerror("Fehler", "Bitte wählen Sie zuerst eine Haupt-Datenbank aus!")
            return
        
        if not self.source_db_paths:
            # Nur Haupt-DB analysieren
            self.log("=== ANALYSE GESTARTET (nur Haupt-DB) ===")
            main_info = self.get_table_info(self.main_db_path)
            if main_info:
                self.log(f"Haupt-DB: {main_info['device_count']} Device-Einträge, {main_info['wifi_count']} WiFi-Einträge")
                self.log(f"Tabellen: {', '.join(main_info['tables'])}")
            self.log("=== ANALYSE ABGESCHLOSSEN ===")
            self.status_var.set("Analyse abgeschlossen - Bereit zum Bereinigen")
            return
        
        self.log("=== ANALYSE GESTARTET ===")
        
        # Analysiere Haupt-DB
        self.log("Analysiere Haupt-Datenbank...")
        main_info = self.get_table_info(self.main_db_path)
        if main_info:
            self.log(f"Haupt-DB: {main_info['device_count']} Device-Einträge, {main_info['wifi_count']} WiFi-Einträge")
            self.log(f"Tabellen: {', '.join(main_info['tables'])}")
        
        # Analysiere Quell-DBs
        total_source_devices = 0
        total_source_wifi = 0
        
        for i, source_path in enumerate(self.source_db_paths):
            self.log(f"Analysiere Quell-DB {i+1}/{len(self.source_db_paths)}...")
            source_info = self.get_table_info(source_path)
            if source_info:
                self.log(f"{os.path.basename(source_path)}: {source_info['device_count']} Device, {source_info['wifi_count']} WiFi")
                total_source_devices += source_info['device_count']
                total_source_wifi += source_info['wifi_count']
        
        self.log(f"=== ANALYSE ABGESCHLOSSEN ===")
        self.log(f"Gesamt zu verarbeiten: {total_source_devices} Device-Einträge, {total_source_wifi} WiFi-Einträge")
        self.status_var.set("Analyse abgeschlossen - Bereit zum Zusammenführen")
    
    def load_devices_from_db(self, db_path):
        """Lädt alle Geräte aus einer Datenbank"""
        devices = []
        try:
            conn = sqlite3.connect(db_path)
            cursor = conn.cursor()
            
            # Lade aus device_data Tabelle - NUR BLUETOOTH, da WiFi in wifi_data ist
            cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='device_data'")
            if cursor.fetchone():
                cursor.execute("""
                    SELECT device_name, device_address, device_type, signal_strength, 
                           encryption_info, latitude, longitude, timestamp, frequency, 
                           channel, wifi_standard, vendor_info, channel_width, max_connection_speed
                    FROM device_data 
                    WHERE latitude != 0 AND longitude != 0 AND device_type = 'BLUETOOTH'
                """)
                
                for row in cursor.fetchall():
                    # Behandle verschiedene Anzahl von Spalten
                    if len(row) >= 14:
                        device_name, device_address, device_type, signal_strength, encryption_info, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed = row
                    else:
                        device_name, device_address, device_type, signal_strength, encryption_info, lat, lon, timestamp = row[:8]
                        frequency = channel = wifi_standard = vendor_info = channel_width = max_speed = None
                    
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
                        'db_source': os.path.basename(db_path)
                    })
            
            # Lade aus wifi_data Tabelle
            cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='wifi_data'")
            if cursor.fetchone():
                # Prüfe verfügbare Spalten
                cursor.execute("PRAGMA table_info(wifi_data)")
                columns = [column[1] for column in cursor.fetchall()]
                has_extended = 'frequency' in columns
                
                if has_extended:
                    cursor.execute("""
                        SELECT ssid, bssid, signal_strength, verschluesselung, 
                               latitude, longitude, timestamp, frequency, channel, 
                               wifi_standard, vendor_info, channel_width, max_connection_speed
                        FROM wifi_data 
                        WHERE latitude != 0 AND longitude != 0
                    """)
                else:
                    cursor.execute("""
                        SELECT ssid, bssid, signal_strength, verschluesselung, 
                               latitude, longitude, timestamp
                        FROM wifi_data 
                        WHERE latitude != 0 AND longitude != 0
                    """)
                
                for row in cursor.fetchall():
                    if has_extended and len(row) >= 13:
                        ssid, bssid, signal_strength, verschluesselung, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed = row[:13]
                    else:
                        ssid, bssid, signal_strength, verschluesselung, lat, lon, timestamp = row[:7]
                        frequency = channel = wifi_standard = vendor_info = channel_width = max_speed = None
                    
                    devices.append({
                        'source': 'wifi_data',
                        'name': ssid,
                        'address': bssid,
                        'type': 'WIFI',
                        'signal': signal_strength,
                        'encryption': verschluesselung,
                        'lat': lat,
                        'lon': lon,
                        'timestamp': timestamp,
                        'frequency': frequency,
                        'channel': channel,
                        'standard': wifi_standard,
                        'vendor': vendor_info,
                        'channel_width': channel_width,
                        'max_speed': max_speed,
                        'db_source': os.path.basename(db_path)
                    })
            
            conn.close()
            return devices
            
        except Exception as e:
            self.log(f"Fehler beim Laden von {os.path.basename(db_path)}: {str(e)}")
            return []
    
    def insert_or_update_device(self, cursor, device, existing_devices):
        """Fügt ein Gerät hinzu oder aktualisiert es mit Bewegungsverfolgung"""
        device_key = f"{device['address']}_{device['type']}"
        
        if device_key in existing_devices:
            existing = existing_devices[device_key]
            
            # Berechne Entfernung zwischen alter und neuer Position
            old_lat = existing.get('lat', 0)
            old_lon = existing.get('lon', 0)
            new_lat = device.get('lat', 0)
            new_lon = device.get('lon', 0)
            
            if old_lat and old_lon and new_lat and new_lon:
                distance = self.calculate_distance(old_lat, old_lon, new_lat, new_lon)
                
                # Wenn Entfernung < 100m, ignoriere den neuen Eintrag
                if distance < 100:
                    return 'skipped_too_close'
                
                # Vergleiche Signalstärke
                current_signal = existing.get('signal', -999)
                new_signal = device.get('signal', -999)
                current_timestamp = existing.get('timestamp', 0)
                new_timestamp = device.get('timestamp', 0)
                
                # Entscheidung basierend auf Signal und Zeit
                should_update_primary = False
                if new_signal > current_signal:
                    should_update_primary = True
                elif new_signal == current_signal and new_timestamp > current_timestamp:
                    should_update_primary = True
                
                if should_update_primary:
                    # Aktualisiere Hauptposition und speichere alte als Last Seen
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
                    
                    self.log(f"Gerät bewegt: {device['address']} - {distance:.1f}m entfernt, Hauptposition aktualisiert")
                    return 'updated_with_movement'
                else:
                    # Neue Position wird als Last Seen gespeichert
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
                    
                    self.log(f"Gerät bewegt: {device['address']} - {distance:.1f}m entfernt, Last Seen aktualisiert")
                    return 'updated_last_seen'
            else:
                # Keine gültigen Koordinaten für Entfernungsberechnung
                current_signal = existing.get('signal', -999)
                new_signal = device.get('signal', -999)
                
                if new_signal > current_signal and self.coordinate_update_var.get():
                    # Aktualisiere nur bei besserem Signal
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
            # Neues Gerät - füge hinzu
            if device['source'] == 'device_data':
                cursor.execute("""
                    INSERT INTO device_data 
                    (device_name, device_address, device_type, signal_strength, encryption_info,
                     latitude, longitude, timestamp, frequency, channel, wifi_standard, 
                     vendor_info, channel_width, max_connection_speed)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (device['name'], device['address'], device['type'], device['signal'],
                      device['encryption'], device['lat'], device['lon'], device['timestamp'],
                      device['frequency'], device['channel'], device['standard'],
                      device['vendor'], device['channel_width'], device['max_speed']))
            elif device['source'] == 'wifi_data':
                cursor.execute("""
                    INSERT INTO wifi_data 
                    (ssid, bssid, signal_strength, verschluesselung, latitude, longitude, timestamp,
                     frequency, channel, wifi_standard, vendor_info, channel_width, max_connection_speed)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (device['name'], device['address'], device['signal'], device['encryption'],
                      device['lat'], device['lon'], device['timestamp'], device['frequency'],
                      device['channel'], device['standard'], device['vendor'],
                      device['channel_width'], device['max_speed']))
            
            return 'added'
    
    def create_backup(self):
        """Erstellt ein Backup der Haupt-Datenbank"""
        if not self.backup_var.get():
            return True
        
        try:
            backup_name = f"{os.path.splitext(self.main_db_path)[0]}_backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}.db"
            shutil.copy2(self.main_db_path, backup_name)
            self.log(f"Backup erstellt: {os.path.basename(backup_name)}")
            return True
        except Exception as e:
            self.log(f"Fehler beim Erstellen des Backups: {str(e)}")
            return False
    
    def combine_databases(self):
        """Führt alle Datenbanken zusammen"""
        if not self.main_db_path:
            messagebox.showerror("Fehler", "Bitte wählen Sie zuerst eine Haupt-Datenbank aus!")
            return
            
        if not self.source_db_paths:
            messagebox.showerror("Fehler", "Bitte fügen Sie mindestens eine Quell-Datenbank hinzu oder nutzen Sie 'DB bereinigen' für eine einzelne Datenbank!")
            return
        
        # Backup erstellen
        if not self.create_backup():
            if not messagebox.askyesno("Warnung", "Backup fehlgeschlagen. Trotzdem fortfahren?"):
                return

        # Stelle sicher, dass alle Quell-DBs die modernen Tabellen/Spalten haben
        for source_db in self.source_db_paths:
            try:
                conn = sqlite3.connect(source_db)
                cursor = conn.cursor()
                self.create_modern_tables(cursor)
                conn.commit()
                conn.close()
                self.log(f"Tabellenstruktur in {os.path.basename(source_db)} aktualisiert.")
            except Exception as e:
                self.log(f"Fehler beim Aktualisieren der Tabellenstruktur in {os.path.basename(source_db)}: {e}")

        self.log("=== ZUSAMMENFÜHRUNG GESTARTET ===")

        try:
            # Lade existierende Geräte aus Haupt-DB
            self.log("Lade existierende Geräte aus Haupt-DB...")
            existing_devices = {}
            main_devices = self.load_devices_from_db(self.main_db_path)
            
            for device in main_devices:
                device_key = f"{device['address']}_{device['type']}"
                existing_devices[device_key] = device
            
            self.log(f"Gefunden: {len(existing_devices)} existierende Geräte")
            
            # Öffne Haupt-DB für Updates
            conn = sqlite3.connect(self.main_db_path)
            cursor = conn.cursor()
            
            # Statistiken
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
            
            # Verarbeite jede Quell-DB
            for i, source_path in enumerate(self.source_db_paths):
                self.log(f"Verarbeite {os.path.basename(source_path)} ({i+1}/{total_sources})...")
                self.progress_var.set((i / total_sources) * 100)
                
                source_devices = self.load_devices_from_db(source_path)
                
                for j, device in enumerate(source_devices):
                    result = self.insert_or_update_device(cursor, device, existing_devices)
                    stats[result] += 1
                    stats['total_processed'] += 1
                    
                    # Aktualisiere existierende Geräte-Liste bei neuen Geräten
                    if result == 'added':
                        device_key = f"{device['address']}_{device['type']}"
                        existing_devices[device_key] = device
                    
                    # Progress Update
                    if j % 100 == 0:
                        self.status_var.set(f"Verarbeite {os.path.basename(source_path)}: {j}/{len(source_devices)}")
                        self.root.update()
            
            # Änderungen speichern
            conn.commit()
            
            # Nach der Zusammenführung: Duplikate prüfen und entfernen
            self.log("Prüfe und entferne Duplikate nach Zusammenführung...")
            removed_duplicates = self.remove_duplicates_after_merge(cursor)
            
            conn.commit()
            conn.close()
            
            self.progress_var.set(100)
            self.status_var.set("Zusammenführung abgeschlossen!")
            
            # Ergebnisse anzeigen
            self.log("=== ZUSAMMENFÜHRUNG ABGESCHLOSSEN ===")
            self.log(f"Verarbeitet: {stats['total_processed']} Geräte")
            self.log(f"Neu hinzugefügt: {stats['added']}")
            self.log(f"Aktualisiert (besseres Signal): {stats['updated']}")
            self.log(f"Bewegung erkannt - Hauptposition aktualisiert: {stats['updated_with_movement']}")
            self.log(f"Bewegung erkannt - Last Seen aktualisiert: {stats['updated_last_seen']}")
            self.log(f"Übersprungen (zu nahe < 100m): {stats['skipped_too_close']}")
            self.log(f"Übersprungen (schlechteres Signal): {stats['skipped_worse_signal']}")
            self.log(f"Übersprungen (besseres existiert): {stats['skipped_better_exists']}")
            
            if removed_duplicates:
                self.log(f"Duplikate entfernt: {removed_duplicates['wifi_removed']} WiFi, {removed_duplicates['bluetooth_removed']} Bluetooth")
            
            # Zeige Bewegungsstatistiken
            total_movements = stats['updated_with_movement'] + stats['updated_last_seen']
            if total_movements > 0:
                self.log(f"Gerätebewegungen erkannt: {total_movements}")
            
            messagebox.showinfo("Erfolg", 
                f"Zusammenführung abgeschlossen!\n\n"
                f"Neu hinzugefügt: {stats['added']}\n"
                f"Aktualisiert: {stats['updated']}\n"
                f"Bewegungen erkannt: {total_movements}\n"
                f"Übersprungen (zu nahe): {stats['skipped_too_close']}\n"
                f"Übersprungen (Signal): {stats['skipped_worse_signal'] + stats['skipped_better_exists']}\n"
                f"Duplikate entfernt: {removed_duplicates.get('total_removed', 0) if removed_duplicates else 0}")
            
        except Exception as e:
            self.log(f"Fehler bei der Zusammenführung: {str(e)}")
            messagebox.showerror("Fehler", f"Fehler bei der Zusammenführung:\n{str(e)}")
    
    def clean_database(self):
        """Bereinigt und sortiert eine Datenbank"""
        if not self.main_db_path:
            messagebox.showerror("Fehler", "Bitte wählen Sie zuerst eine Datenbank zum Bereinigen aus!")
            return
        
        # Bestätigung
        if not messagebox.askyesno("Bestätigung", 
            f"Möchten Sie die Datenbank bereinigen und sortieren?\n\n"
            f"- WiFi-Daten werden in wifi_data verschoben\n"
            f"- Bluetooth-Daten werden in device_data verschoben\n"
            f"- Duplikate werden entfernt\n"
            f"- Ein Backup wird erstellt\n\n"
            f"Datei: {os.path.basename(self.main_db_path)}"):
            return
        
        # Backup erstellen
        if not self.create_backup():
            if not messagebox.askyesno("Warnung", "Backup fehlgeschlagen. Trotzdem fortfahren?"):
                return
        
        self.log("=== DATENBANK-BEREINIGUNG GESTARTET ===")
        
        try:
            conn = sqlite3.connect(self.main_db_path)
            cursor = conn.cursor()
            
            # Analysiere aktuelle Struktur
            cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
            existing_tables = [row[0] for row in cursor.fetchall()]
            
            self.log(f"Vorhandene Tabellen: {', '.join(existing_tables)}")
            
            # Erstelle moderne Tabellenstruktur falls nicht vorhanden
            self.create_modern_tables(cursor)
            
            # EINFACHE BEREINIGUNG: Nur WiFi aus device_data verschieben und Duplikate entfernen
            self.log("Verschiebe WiFi-Daten von device_data zu wifi_data...")
            self.migrate_wifi_from_device_data_simple(cursor)
            
            # Entferne Duplikate in beiden Tabellen
            self.log("Entferne Duplikate in wifi_data...")
            wifi_duplicates_removed = self.remove_wifi_duplicates(cursor)
            
            self.log("Entferne Duplikate in device_data...")
            bluetooth_duplicates_removed = self.remove_bluetooth_duplicates(cursor)
            
            # Alte Tabellen löschen (optional)
            if messagebox.askyesno("Aufräumen", "Möchten Sie die alten/leeren Tabellen löschen?"):
                self.cleanup_old_tables(cursor, existing_tables)
            
            conn.commit()
            conn.close()
            
            self.log("=== BEREINIGUNG ABGESCHLOSSEN ===")
            self.log(f"WiFi-Duplikate entfernt: {wifi_duplicates_removed}")
            self.log(f"Bluetooth-Duplikate entfernt: {bluetooth_duplicates_removed}")
            
            messagebox.showinfo("Erfolg", 
                f"Datenbank bereinigt!\n\n"
                f"WiFi-Duplikate entfernt: {wifi_duplicates_removed}\n"
                f"Bluetooth-Duplikate entfernt: {bluetooth_duplicates_removed}\n\n"
                f"Alle Daten sind jetzt in den korrekten Tabellen sortiert.")
                
        except Exception as e:
            self.log(f"Fehler bei der Bereinigung: {str(e)}")
            messagebox.showerror("Fehler", f"Fehler bei der Bereinigung:\n{str(e)}")
    
    def repair_database(self):
        """Repariert und aktualisiert die Datenbankstruktur"""
        if not self.main_db_path:
            messagebox.showerror("Fehler", "Bitte wählen Sie zuerst eine Datenbank zum Reparieren aus!")
            return
        
        # Bestätigung
        if not messagebox.askyesno("Bestätigung", 
            f"Möchten Sie die Datenbankstruktur reparieren und aktualisieren?\n\n"
            f"- Fehlende Bewegungsverfolgung-Spalten werden hinzugefügt\n"
            f"- Tabellenstrukturen werden modernisiert\n"
            f"- Ein Backup wird erstellt\n\n"
            f"Datei: {os.path.basename(self.main_db_path)}"):
            return
        
        # Backup erstellen
        if not self.create_backup():
            if not messagebox.askyesno("Warnung", "Backup fehlgeschlagen. Trotzdem fortfahren?"):
                return
        
        self.log("=== DATENBANK-REPARATUR GESTARTET ===")
        
        try:
            conn = sqlite3.connect(self.main_db_path)
            cursor = conn.cursor()
            
            # Analysiere aktuelle Struktur
            cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
            existing_tables = [row[0] for row in cursor.fetchall()]
            
            self.log(f"Vorhandene Tabellen: {', '.join(existing_tables)}")
            
            # Statistiken für Reparatur
            repair_stats = {
                'tables_created': 0,
                'columns_added': 0,
                'tables_analyzed': len(existing_tables)
            }
            
            # Erstelle moderne Tabellenstruktur falls nicht vorhanden
            self.log("Prüfe und erstelle moderne Tabellenstrukturen...")
            if 'wifi_data' not in existing_tables:
                self.create_modern_tables(cursor)
                repair_stats['tables_created'] += 1
                self.log("Tabelle 'wifi_data' erstellt")
            
            if 'device_data' not in existing_tables:
                if repair_stats['tables_created'] == 0:  # Nur wenn wifi_data schon existierte
                    self.create_modern_tables(cursor)
                    repair_stats['tables_created'] += 1
                self.log("Tabelle 'device_data' erstellt")
            
            # Füge Bewegungsverfolgung-Spalten hinzu
            self.log("Prüfe und füge Bewegungsverfolgung-Spalten hinzu...")
            columns_before = self.count_movement_columns(cursor)
            self.add_movement_tracking_columns(cursor)
            columns_after = self.count_movement_columns(cursor)
            repair_stats['columns_added'] = columns_after - columns_before
            
            # Prüfe alle Tabellen auf fehlende Standard-Spalten
            self.log("Prüfe und repariere Standard-Spalten...")
            additional_columns = self.repair_standard_columns(cursor, existing_tables)
            repair_stats['columns_added'] += additional_columns
            
            # Erstelle Indizes für bessere Performance
            self.log("Erstelle Performance-Indizes...")
            self.create_performance_indexes(cursor)
            
            conn.commit()
            conn.close()
            
            self.log("=== REPARATUR ABGESCHLOSSEN ===")
            self.log(f"Tabellen erstellt: {repair_stats['tables_created']}")
            self.log(f"Spalten hinzugefügt: {repair_stats['columns_added']}")
            self.log(f"Tabellen analysiert: {repair_stats['tables_analyzed']}")
            
            messagebox.showinfo("Erfolg", 
                f"Datenbank-Reparatur abgeschlossen!\n\n"
                f"Tabellen erstellt: {repair_stats['tables_created']}\n"
                f"Spalten hinzugefügt: {repair_stats['columns_added']}\n"
                f"Tabellen analysiert: {repair_stats['tables_analyzed']}\n\n"
                f"Die Datenbank ist jetzt auf dem neuesten Stand.")
                
        except Exception as e:
            self.log(f"Fehler bei der Reparatur: {str(e)}")
            messagebox.showerror("Fehler", f"Fehler bei der Reparatur:\n{str(e)}")
    
    def count_movement_columns(self, cursor):
        """Zählt vorhandene Bewegungsverfolgung-Spalten"""
        count = 0
        
        # Prüfe wifi_data
        try:
            cursor.execute("PRAGMA table_info(wifi_data)")
            wifi_columns = [col[1] for col in cursor.fetchall()]
            movement_cols = ['last_seen_latitude', 'last_seen_longitude', 'last_seen_timestamp', 'movement_distance']
            count += sum(1 for col in movement_cols if col in wifi_columns)
        except:
            pass
        
        # Prüfe device_data
        try:
            cursor.execute("PRAGMA table_info(device_data)")
            device_columns = [col[1] for col in cursor.fetchall()]
            movement_cols = ['last_seen_latitude', 'last_seen_longitude', 'last_seen_timestamp', 'movement_distance']
            count += sum(1 for col in movement_cols if col in device_columns)
        except:
            pass
        
        return count
    
    def repair_standard_columns(self, cursor, existing_tables):
        """Repariert und fügt fehlende Standard-Spalten hinzu"""
        columns_added = 0
        
        # Standard-Spalten für wifi_data
        wifi_standard_columns = {
            'frequency': 'INTEGER',
            'channel': 'INTEGER', 
            'wifi_standard': 'TEXT',
            'vendor_info': 'TEXT',
            'channel_width': 'INTEGER',
            'max_connection_speed': 'INTEGER'
        }
        
        # Standard-Spalten für device_data
        device_standard_columns = {
            'frequency': 'INTEGER',
            'channel': 'INTEGER',
            'wifi_standard': 'TEXT',
            'vendor_info': 'TEXT',
            'channel_width': 'INTEGER',
            'max_connection_speed': 'INTEGER'
        }
        
        # Repariere wifi_data
        if 'wifi_data' in existing_tables:
            try:
                cursor.execute("PRAGMA table_info(wifi_data)")
                wifi_columns = [col[1] for col in cursor.fetchall()]
                
                for col_name, col_type in wifi_standard_columns.items():
                    if col_name not in wifi_columns:
                        try:
                            cursor.execute(f"ALTER TABLE wifi_data ADD COLUMN {col_name} {col_type}")
                            self.log(f"Spalte '{col_name}' zu wifi_data hinzugefügt")
                            columns_added += 1
                        except Exception as e:
                            self.log(f"Warnung: Konnte Spalte {col_name} nicht zu wifi_data hinzufügen: {str(e)}")
            except Exception as e:
                self.log(f"Fehler beim Reparieren von wifi_data: {str(e)}")
        
        # Repariere device_data
        if 'device_data' in existing_tables:
            try:
                cursor.execute("PRAGMA table_info(device_data)")
                device_columns = [col[1] for col in cursor.fetchall()]
                
                for col_name, col_type in device_standard_columns.items():
                    if col_name not in device_columns:
                        try:
                            cursor.execute(f"ALTER TABLE device_data ADD COLUMN {col_name} {col_type}")
                            self.log(f"Spalte '{col_name}' zu device_data hinzugefügt")
                            columns_added += 1
                        except Exception as e:
                            self.log(f"Warnung: Konnte Spalte {col_name} nicht zu device_data hinzufügen: {str(e)}")
            except Exception as e:
                self.log(f"Fehler beim Reparieren von device_data: {str(e)}")
        
        return columns_added
    
    def create_performance_indexes(self, cursor):
        """Erstellt Indizes für bessere Performance"""
        indexes = [
            # WiFi-Daten Indizes
            ("idx_wifi_bssid", "wifi_data", "bssid"),
            ("idx_wifi_signal", "wifi_data", "signal_strength"),
            ("idx_wifi_coords", "wifi_data", "latitude, longitude"),
            ("idx_wifi_timestamp", "wifi_data", "timestamp"),
            
            # Device-Daten Indizes
            ("idx_device_address", "device_data", "device_address"),
            ("idx_device_type", "device_data", "device_type"),
            ("idx_device_signal", "device_data", "signal_strength"),
            ("idx_device_coords", "device_data", "latitude, longitude"),
            ("idx_device_timestamp", "device_data", "timestamp"),
        ]
        
        for index_name, table_name, columns in indexes:
            try:
                cursor.execute(f"CREATE INDEX IF NOT EXISTS {index_name} ON {table_name} ({columns})")
                self.log(f"Index '{index_name}' erstellt/überprüft")
            except Exception as e:
                self.log(f"Warnung: Konnte Index {index_name} nicht erstellen: {str(e)}")
    
    def create_modern_tables(self, cursor):
        """Erstellt moderne Tabellenstrukturen mit Bewegungsverfolgung"""
        # WiFi-Daten Tabelle mit Last Seen Koordinaten
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS wifi_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ssid TEXT,
                bssid TEXT NOT NULL,
                signal_strength INTEGER,
                verschluesselung TEXT,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                timestamp INTEGER,
                frequency INTEGER,
                channel INTEGER,
                wifi_standard TEXT,
                vendor_info TEXT,
                channel_width INTEGER,
                max_connection_speed INTEGER,
                last_seen_latitude REAL,
                last_seen_longitude REAL,
                last_seen_timestamp INTEGER,
                movement_distance REAL,
                UNIQUE(bssid)
            )
        """)
        
        # Device-Daten Tabelle mit Last Seen Koordinaten
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS device_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_name TEXT,
                device_address TEXT NOT NULL,
                device_type TEXT NOT NULL,
                signal_strength INTEGER,
                encryption_info TEXT,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                timestamp INTEGER,
                frequency INTEGER,
                channel INTEGER,
                wifi_standard TEXT,
                vendor_info TEXT,
                channel_width INTEGER,
                max_connection_speed INTEGER,
                last_seen_latitude REAL,
                last_seen_longitude REAL,
                last_seen_timestamp INTEGER,
                movement_distance REAL,
                UNIQUE(device_address, device_type)
            )
        """)
        
        # Neue Spalten zu bestehenden Tabellen hinzufügen falls sie nicht existieren
        self.add_movement_tracking_columns(cursor)
    
    def add_movement_tracking_columns(self, cursor):
        """Fügt Bewegungsverfolgung-Spalten zu bestehenden Tabellen hinzu"""
        try:
            # Prüfe wifi_data Tabelle
            cursor.execute("PRAGMA table_info(wifi_data)")
            wifi_columns = [col[1] for col in cursor.fetchall()]
            
            if 'last_seen_latitude' not in wifi_columns:
                cursor.execute("ALTER TABLE wifi_data ADD COLUMN last_seen_latitude REAL")
                self.log("Spalte 'last_seen_latitude' zu wifi_data hinzugefügt")
            
            if 'last_seen_longitude' not in wifi_columns:
                cursor.execute("ALTER TABLE wifi_data ADD COLUMN last_seen_longitude REAL")
                self.log("Spalte 'last_seen_longitude' zu wifi_data hinzugefügt")
            
            if 'last_seen_timestamp' not in wifi_columns:
                cursor.execute("ALTER TABLE wifi_data ADD COLUMN last_seen_timestamp INTEGER")
                self.log("Spalte 'last_seen_timestamp' zu wifi_data hinzugefügt")
            
            if 'movement_distance' not in wifi_columns:
                cursor.execute("ALTER TABLE wifi_data ADD COLUMN movement_distance REAL")
                self.log("Spalte 'movement_distance' zu wifi_data hinzugefügt")
            
            # Prüfe device_data Tabelle
            cursor.execute("PRAGMA table_info(device_data)")
            device_columns = [col[1] for col in cursor.fetchall()]
            
            if 'last_seen_latitude' not in device_columns:
                cursor.execute("ALTER TABLE device_data ADD COLUMN last_seen_latitude REAL")
                self.log("Spalte 'last_seen_latitude' zu device_data hinzugefügt")
            
            if 'last_seen_longitude' not in device_columns:
                cursor.execute("ALTER TABLE device_data ADD COLUMN last_seen_longitude REAL")
                self.log("Spalte 'last_seen_longitude' zu device_data hinzugefügt")
            
            if 'last_seen_timestamp' not in device_columns:
                cursor.execute("ALTER TABLE device_data ADD COLUMN last_seen_timestamp INTEGER")
                self.log("Spalte 'last_seen_timestamp' zu device_data hinzugefügt")
            
            if 'movement_distance' not in device_columns:
                cursor.execute("ALTER TABLE device_data ADD COLUMN movement_distance REAL")
                self.log("Spalte 'movement_distance' zu device_data hinzugefügt")
                
        except Exception as e:
            self.log(f"Fehler beim Hinzufügen der Bewegungsverfolgung-Spalten: {str(e)}")
    
    def calculate_distance(self, lat1, lon1, lat2, lon2):
        """Berechnet die Entfernung zwischen zwei GPS-Koordinaten in Metern (Haversine-Formel)"""
        import math
        
        # Radius der Erde in Kilometern
        R = 6371.0
        
        # Koordinaten in Radiant umwandeln
        lat1_rad = math.radians(lat1)
        lon1_rad = math.radians(lon1)
        lat2_rad = math.radians(lat2)
        lon2_rad = math.radians(lon2)
        
        # Unterschiede berechnen
        dlat = lat2_rad - lat1_rad
        dlon = lon2_rad - lon1_rad
        
        # Haversine-Formel
        a = math.sin(dlat/2)**2 + math.cos(lat1_rad) * math.cos(lat2_rad) * math.sin(dlon/2)**2
        c = 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))
        distance_km = R * c
        
        # In Meter umwandeln
        return distance_km * 1000
    
    def extract_data_from_device_table(self, cursor):
        """Extrahiert Daten aus device_data Tabelle"""
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
            self.log(f"Fehler beim Extrahieren aus device_data: {str(e)}")
        
        return data
    
    def extract_data_from_wifi_table(self, cursor):
        """Extrahiert Daten aus wifi_data Tabelle"""
        data = []
        try:
            cursor.execute("PRAGMA table_info(wifi_data)")
            columns = [col[1] for col in cursor.fetchall()]
            
            if len(columns) >= 7:
                cursor.execute("""
                    SELECT ssid, bssid, signal_strength, verschluesselung,
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
            self.log(f"Fehler beim Extrahieren aus wifi_data: {str(e)}")
        
        return data
    
    def extract_data_from_legacy_table(self, cursor, table_name):
        """Extrahiert Daten aus alten Tabellenformaten"""
        data = []
        try:
            cursor.execute(f"PRAGMA table_info({table_name})")
            columns = [col[1] for col in cursor.fetchall()]
            
            # Bestimme Tabellentyp basierend auf Spalten
            if 'bssid' in columns or 'ssid' in columns:
                # WiFi Tabelle
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
                    if row[1]:  # address nicht leer
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
                # Gemischte Tabelle
                cursor.execute(f"""
                    SELECT device_name, device_address, device_type, signal_strength,
                           COALESCE(encryption_info, '') as encryption,
                           latitude, longitude, timestamp
                    FROM {table_name} 
                    WHERE latitude != 0 AND longitude != 0
                """)
                
                for row in cursor.fetchall():
                    if row[1]:  # address nicht leer
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
            self.log(f"Fehler beim Extrahieren aus {table_name}: {str(e)}")
        
        return data
    
    def clean_and_sort_data(self, all_data):
        """Bereinigt und sortiert Daten nach Typ"""
        wifi_dict = {}
        bluetooth_dict = {}
        
        for item in all_data:
            if not item['address']:
                continue
                
            # Bestimme Typ basierend auf verschiedenen Kriterien
            device_type = item['type'].upper() if item['type'] else ''
            
            # WiFi-Erkennung
            if (device_type in ['WIFI', 'WI-FI', 'WIRELESS'] or 
                (item['name'] and len(item['address']) == 17 and ':' in item['address'])):
                
                key = item['address'].upper()
                
                if key not in wifi_dict or self.is_better_entry(item, wifi_dict[key]):
                    wifi_dict[key] = {
                        'ssid': item['name'] or '',
                        'bssid': key,
                        'signal_strength': item['signal'] or -100,
                        'verschluesselung': item['encryption'] or '',
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
            
            # Bluetooth-Erkennung
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
        
        self.log(f"Nach Bereinigung: {len(wifi_dict)} WiFi, {len(bluetooth_dict)} Bluetooth")
        
        return list(wifi_dict.values()), list(bluetooth_dict.values())
    
    def is_better_entry(self, new_item, existing_item):
        """Prüft ob ein neuer Eintrag besser ist als der existierende"""
        new_signal = new_item.get('signal', -999)
        existing_signal = existing_item.get('signal_strength', existing_item.get('signal', -999))
        
        new_timestamp = new_item.get('timestamp', 0)
        existing_timestamp = existing_item.get('timestamp', 0)
        
        # Besseres Signal bevorzugen
        if new_signal > existing_signal:
            return True
        
        # Bei gleichem Signal, neueren Timestamp bevorzugen
        if new_signal == existing_signal and new_timestamp > existing_timestamp:
            return True
            
        return False
    
    def insert_cleaned_data(self, cursor, wifi_data, bluetooth_data):
        """Fügt bereinigte Daten in die korrekten Tabellen ein"""
        # WiFi-Daten: nur neue hinzufügen, da Migration bereits erfolgt ist
        for wifi in wifi_data:
            try:
                cursor.execute("""
                    INSERT OR IGNORE INTO wifi_data 
                    (ssid, bssid, signal_strength, verschluesselung, latitude, longitude, timestamp,
                     frequency, channel, wifi_standard, vendor_info, channel_width, max_connection_speed)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (wifi['ssid'], wifi['bssid'], wifi['signal_strength'], wifi['verschluesselung'],
                      wifi['latitude'], wifi['longitude'], wifi['timestamp'], wifi['frequency'],
                      wifi['channel'], wifi['wifi_standard'], wifi['vendor_info'],
                      wifi['channel_width'], wifi['max_connection_speed']))
            except Exception as e:
                self.log(f"Fehler beim Einfügen WiFi {wifi['bssid']}: {str(e)}")
        
        # Bluetooth-Daten: nur neue hinzufügen, bestehende behalten
        for bt in bluetooth_data:
            try:
                cursor.execute("""
                    INSERT OR IGNORE INTO device_data 
                    (device_name, device_address, device_type, signal_strength, encryption_info,
                     latitude, longitude, timestamp, frequency, channel, wifi_standard,
                     vendor_info, channel_width, max_connection_speed)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (bt['device_name'], bt['device_address'], bt['device_type'], bt['signal_strength'],
                      bt['encryption_info'], bt['latitude'], bt['longitude'], bt['timestamp'],
                      bt['frequency'], bt['channel'], bt['wifi_standard'], bt['vendor_info'],
                      bt['channel_width'], bt['max_connection_speed']))
            except Exception as e:
                self.log(f"Fehler beim Einfügen Bluetooth {bt['device_address']}: {str(e)}")
    
    def cleanup_old_tables(self, cursor, existing_tables):
        """Löscht alte oder leere Tabellen"""
        tables_to_check = ['wifi_networks', 'bluetooth_devices', 'scanned_devices']
        
        for table in tables_to_check:
            if table in existing_tables:
                try:
                    # Prüfe ob Tabelle leer ist
                    cursor.execute(f"SELECT COUNT(*) FROM {table}")
                    count = cursor.fetchone()[0]
                    
                    if count == 0:
                        cursor.execute(f"DROP TABLE {table}")
                        self.log(f"Leere Tabelle gelöscht: {table}")
                    else:
                        self.log(f"Tabelle {table} behalten (enthält noch {count} Einträge)")
                        
                except Exception as e:
                    self.log(f"Fehler beim Prüfen/Löschen von {table}: {str(e)}")
    
    def migrate_wifi_from_device_data(self, cursor, wifi_data):
        """Migriert WiFi-Daten von device_data zu wifi_data und entfernt sie aus device_data"""
        try:
            # Erstelle ein Dictionary der WiFi-Daten nach BSSID für schnellen Zugriff
            wifi_dict = {wifi['bssid']: wifi for wifi in wifi_data}
            
            # Hole alle WiFi-Einträge aus device_data
            cursor.execute("""
                SELECT device_name, device_address, signal_strength, encryption_info,
                       latitude, longitude, timestamp, frequency, channel, wifi_standard,
                       vendor_info, channel_width, max_connection_speed
                FROM device_data 
                WHERE device_type = 'WIFI' AND latitude != 0 AND longitude != 0
            """)
            
            device_wifi_entries = cursor.fetchall()
            self.log(f"Gefunden: {len(device_wifi_entries)} WiFi-Einträge in device_data zum Migrieren")
            
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
                
                # Prüfe ob bereits in wifi_data vorhanden
                if bssid in wifi_dict:
                    existing_wifi = wifi_dict[bssid]
                    existing_signal = existing_wifi['signal_strength']
                    
                    # Vergleiche Signalstärke
                    if signal_strength and signal_strength > existing_signal:
                        # Besseres Signal in device_data - aktualisiere wifi_data
                        cursor.execute("""
                            UPDATE wifi_data 
                            SET ssid = ?, signal_strength = ?, verschluesselung = ?,
                                latitude = ?, longitude = ?, timestamp = ?,
                                frequency = ?, channel = ?, wifi_standard = ?,
                                vendor_info = ?, channel_width = ?, max_connection_speed = ?
                            WHERE bssid = ?
                        """, (device_name, signal_strength, encryption_info, lat, lon, timestamp,
                              frequency, channel, wifi_standard, vendor_info, channel_width, max_speed, bssid))
                        updated_count += 1
                        self.log(f"WiFi aktualisiert: {device_name} ({bssid}) - besseres Signal")
                    else:
                        # Schlechteres Signal - behalte bestehende Daten
                        skipped_count += 1
                else:
                    # Nicht in wifi_data vorhanden - füge hinzu
                    cursor.execute("""
                        INSERT INTO wifi_data 
                        (ssid, bssid, signal_strength, verschluesselung, latitude, longitude, timestamp,
                         frequency, channel, wifi_standard, vendor_info, channel_width, max_connection_speed)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, (device_name, bssid, signal_strength, encryption_info, lat, lon, timestamp,
                          frequency, channel, wifi_standard, vendor_info, channel_width, max_speed))
                    migrated_count += 1
            
            # Entferne alle WiFi-Einträge aus device_data
            cursor.execute("DELETE FROM device_data WHERE device_type = 'WIFI'")
            deleted_count = cursor.rowcount
            
            self.log(f"Migration abgeschlossen:")
            self.log(f"  - {migrated_count} neue WiFi-Einträge zu wifi_data hinzugefügt")
            self.log(f"  - {updated_count} WiFi-Einträge in wifi_data aktualisiert")
            self.log(f"  - {skipped_count} WiFi-Einträge übersprungen (schlechteres Signal)")
            self.log(f"  - {deleted_count} WiFi-Einträge aus device_data entfernt")
            
        except Exception as e:
            self.log(f"Fehler bei der WiFi-Migration: {str(e)}")
    
    def migrate_wifi_from_device_data_simple(self, cursor):
        """Einfache Migration: Verschiebt WiFi-Daten von device_data zu wifi_data"""
        try:
            # Prüfe ob WiFi-Einträge in device_data existieren
            cursor.execute("SELECT COUNT(*) FROM device_data WHERE device_type = 'WIFI'")
            wifi_count = cursor.fetchone()[0]
            
            if wifi_count == 0:
                self.log("Keine WiFi-Einträge in device_data gefunden.")
                return
            
            self.log(f"Gefunden: {wifi_count} WiFi-Einträge in device_data")
            
            # Verschiebe WiFi-Daten zu wifi_data
            cursor.execute("""
                INSERT OR IGNORE INTO wifi_data 
                (ssid, bssid, signal_strength, verschluesselung, latitude, longitude, timestamp,
                 frequency, channel, wifi_standard, vendor_info, channel_width, max_connection_speed)
                SELECT device_name, device_address, signal_strength, encryption_info, latitude, longitude, timestamp,
                       frequency, channel, wifi_standard, vendor_info, channel_width, max_connection_speed
                FROM device_data 
                WHERE device_type = 'WIFI' AND latitude != 0 AND longitude != 0
            """)
            
            inserted_count = cursor.rowcount
            
            # Entferne WiFi-Einträge aus device_data
            cursor.execute("DELETE FROM device_data WHERE device_type = 'WIFI'")
            deleted_count = cursor.rowcount
            
            self.log(f"Migration abgeschlossen: {inserted_count} eingefügt, {deleted_count} aus device_data entfernt")
            
        except Exception as e:
            self.log(f"Fehler bei der einfachen WiFi-Migration: {str(e)}")
    
    def remove_wifi_duplicates(self, cursor):
        """Entfernt WiFi-Duplikate basierend auf BSSID"""
        try:
            # Finde Duplikate
            cursor.execute("""
                SELECT bssid, COUNT(*) as count 
                FROM wifi_data 
                GROUP BY bssid 
                HAVING COUNT(*) > 1
            """)
            duplicates = cursor.fetchall()
            
            if not duplicates:
                self.log("Keine WiFi-Duplikate gefunden.")
                return 0
            
            total_removed = 0
            for bssid, count in duplicates:
                # Behalte nur den besten Eintrag (bestes Signal, neuester Timestamp)
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
                    self.log(f"WiFi {bssid}: {removed} Duplikate entfernt")
            
            return total_removed
            
        except Exception as e:
            self.log(f"Fehler beim Entfernen von WiFi-Duplikaten: {str(e)}")
            return 0
    
    def remove_bluetooth_duplicates(self, cursor):
        """Entfernt Bluetooth-Duplikate basierend auf device_address"""
        try:
            # Finde Duplikate
            cursor.execute("""
                SELECT device_address, COUNT(*) as count 
                FROM device_data 
                WHERE device_type = 'BLUETOOTH'
                GROUP BY device_address 
                HAVING COUNT(*) > 1
            """)
            duplicates = cursor.fetchall()
            
            if not duplicates:
                self.log("Keine Bluetooth-Duplikate gefunden.")
                return 0
            
            total_removed = 0
            for device_address, count in duplicates:
                # Behalte nur den besten Eintrag (bestes Signal, neuester Timestamp)
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
                    self.log(f"Bluetooth {device_address}: {removed} Duplikate entfernt")
            
            return total_removed
            
        except Exception as e:
            self.log(f"Fehler beim Entfernen von Bluetooth-Duplikaten: {str(e)}")
            return 0
    
    def remove_duplicates_after_merge(self, cursor):
        """Entfernt Duplikate nach der Zusammenführung basierend auf besserem Signal und neuerem Timestamp"""
        try:
            wifi_removed = 0
            bluetooth_removed = 0
            
            # 1. WiFi-Duplikate in wifi_data entfernen
            self.log("Entferne WiFi-Duplikate basierend auf BSSID...")
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
            
            # Einfachere Methode für WiFi-Duplikate
            if wifi_removed == 0:
                # Finde und entferne WiFi-Duplikate mit besserer Logik
                cursor.execute("""
                    SELECT bssid, COUNT(*) as count 
                    FROM wifi_data 
                    GROUP BY bssid 
                    HAVING COUNT(*) > 1
                """)
                wifi_duplicates = cursor.fetchall()
                
                for bssid, count in wifi_duplicates:
                    # Behalte den Eintrag mit bestem Signal und neuestem Timestamp
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
            
            # 2. Bluetooth-Duplikate in device_data entfernen
            self.log("Entferne Bluetooth-Duplikate basierend auf device_address...")
            cursor.execute("""
                SELECT device_address, COUNT(*) as count 
                FROM device_data 
                WHERE device_type = 'BLUETOOTH'
                GROUP BY device_address 
                HAVING COUNT(*) > 1
            """)
            bluetooth_duplicates = cursor.fetchall()
            
            for device_address, count in bluetooth_duplicates:
                # Behalte den Eintrag mit bestem Signal und neuestem Timestamp
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
                self.log(f"Duplikate entfernt: {wifi_removed} WiFi, {bluetooth_removed} Bluetooth (Gesamt: {total_removed})")
            else:
                self.log("Keine Duplikate gefunden.")
            
            return {
                'wifi_removed': wifi_removed,
                'bluetooth_removed': bluetooth_removed,
                'total_removed': total_removed
            }
            
        except Exception as e:
            self.log(f"Fehler beim Entfernen von Duplikaten: {str(e)}")
            return None
    
    def run(self):
        """Startet die GUI"""
        self.root.mainloop()

def main():
    """Hauptfunktion"""
    app = DatabaseCombiner()
    app.run()

if __name__ == "__main__":
    main()
