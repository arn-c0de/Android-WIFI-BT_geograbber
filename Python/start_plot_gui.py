import sqlite3
import folium
from folium import plugins
import tkinter as tk
from tkinter import filedialog, messagebox
import os
import webbrowser
import tempfile
import json

def get_vendor_from_oui(bssid):
    """Ermittelt den Hersteller anhand der OUI (ersten 6 Zeichen der MAC-Adresse)"""
    if not bssid or len(bssid) < 8:
        return "Unknown"
    
    oui = bssid[:8].upper().replace(":", "").replace("-", "")
    if len(oui) < 6:
        return "Unknown"
    
    # Bekannte Hersteller-OUIs
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
    """Öffnet einen Dateiauswahl-Dialog für .db Dateien"""
    root = tk.Tk()
    root.withdraw()  # Versteckt das Hauptfenster
    
    # Startverzeichnis ist der Programmordner
    initial_dir = os.path.dirname(os.path.abspath(__file__))
    
    file_path = filedialog.askopenfilename(
        title="Wähle eine WiFi-Scanner Datenbank aus",
        initialdir=initial_dir,
        filetypes=[("Database files", "*.db"), ("All files", "*.*")]
    )
    
    root.destroy()
    return file_path

def load_wifi_data(db_path):
    """Lädt WiFi- und Bluetooth-Daten aus der SQLite-Datenbank"""
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        
        # Prüfe zuerst ob die neue device_data Tabelle existiert
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='device_data'")
        has_device_table = cursor.fetchone() is not None
        
        all_data = []
        
        if has_device_table:
            # Prüfe verfügbare Spalten in device_data Tabelle
            cursor.execute("PRAGMA table_info(device_data)")
            device_columns = [col[1] for col in cursor.fetchall()]
            has_movement_tracking = 'last_seen_latitude' in device_columns
            
            # Nur Bluetooth-Daten aus device_data laden (WiFi ist in wifi_data)
            # Alle Bluetooth-Geräte laden (auch Unknown Device für "Bluetooth" Filter)
            if has_movement_tracking:
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
            
            # Konvertiere zu erweitertem Format
            for row in device_data:
                if has_movement_tracking and len(row) >= 18:  # Neue Struktur mit Bewegungsverfolgung
                    device_name, device_address, device_type, signal_strength, encryption_info, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed, last_seen_lat, last_seen_lon, last_seen_timestamp, movement_distance = row
                    # Vendor-Info aus DB verwenden, falls nicht vorhanden aus MAC ableiten
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
                        'last_seen_lat': last_seen_lat,
                        'last_seen_lon': last_seen_lon,
                        'last_seen_timestamp': last_seen_timestamp,
                        'movement_distance': movement_distance,
                        'source_table': 'device_data'
                    })
                elif len(row) >= 14:  # Erweiterte Daten verfügbar (ohne Bewegungsverfolgung)
                    device_name, device_address, device_type, signal_strength, encryption_info, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed = row
                    # Vendor-Info aus DB verwenden, falls nicht vorhanden aus MAC ableiten
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
                        'last_seen_lat': None,
                        'last_seen_lon': None,
                        'last_seen_timestamp': None,
                        'movement_distance': None,
                        'source_table': 'device_data'
                    })
                else:  # Alte Datenstruktur
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
                        'last_seen_lat': None,
                        'last_seen_lon': None,
                        'last_seen_timestamp': None,
                        'movement_distance': None,
                        'source_table': 'device_data'
                    })
        
        # Prüfe ob wifi_data Tabelle existiert
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='wifi_data'")
        has_wifi_table = cursor.fetchone() is not None
        
        if has_wifi_table:
            # Alle WiFi-Daten aus wifi_data laden
            # Prüfe ob erweiterte Felder in wifi_data existieren
            cursor.execute("PRAGMA table_info(wifi_data)")
            columns = [column[1] for column in cursor.fetchall()]
            has_extended_fields = 'frequency' in columns
            
            if has_extended_fields:
                query = """
                SELECT ssid, bssid, signal_strength, verschluesselung, 
                       latitude, longitude, timestamp, frequency, channel, 
                       wifi_standard, vendor_info, channel_width, max_connection_speed
                FROM wifi_data 
                WHERE latitude != 0 AND longitude != 0
                ORDER BY signal_strength DESC
                """
            else:
                query = """
                SELECT ssid, bssid, signal_strength, verschluesselung, 
                       latitude, longitude, timestamp
                FROM wifi_data 
                WHERE latitude != 0 AND longitude != 0
                ORDER BY signal_strength DESC
                """
            
            cursor.execute(query)
            wifi_data = cursor.fetchall()
            
            # Konvertiere WiFi-Daten
            for row in wifi_data:
                if has_extended_fields and len(row) >= 13:
                    ssid, bssid, signal_strength, verschluesselung, lat, lon, timestamp, frequency, channel, wifi_standard, vendor_info, channel_width, max_speed = row[:13]
                    if not vendor_info or vendor_info == "Unknown":
                        vendor_info = get_vendor_from_oui(bssid)
                else:
                    ssid, bssid, signal_strength, verschluesselung, lat, lon, timestamp = row[:7]
                    frequency = channel = wifi_standard = channel_width = max_speed = None
                    vendor_info = get_vendor_from_oui(bssid)
                
                all_data.append({
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
                    'source_table': 'wifi_data'
                })
        
        conn.close()
        
        # Deduplizierung: Entferne Duplikate basierend auf BSSID/MAC-Adresse
        # Bevorzuge neueste Einträge (höchster Zeitstempel) und bessere Signalstärke
        seen_devices = {}
        deduplicated_data = []
        
        for device in all_data:
            device_key = f"{device['address']}_{device['type']}"
            
            if device_key in seen_devices:
                existing = seen_devices[device_key]
                
                # Vergleiche zuerst Zeitstempel (neuester gewinnt)
                if device['timestamp'] > existing['timestamp']:
                    # Neuerer Eintrag - ersetze
                    seen_devices[device_key] = device
                elif device['timestamp'] == existing['timestamp']:
                    # Gleicher Zeitstempel - vergleiche Signal
                    current_signal = existing.get('signal', -999)
                    new_signal = device.get('signal', -999)
                    if new_signal > current_signal:
                        seen_devices[device_key] = device
                # Sonst behalte den existierenden
            else:
                seen_devices[device_key] = device
        
        # Erstelle finale Liste ohne Duplikate
        deduplicated_data = list(seen_devices.values())
        
        print(f"Vor Deduplizierung: {len(all_data)} Geräte")
        print(f"Nach Deduplizierung: {len(deduplicated_data)} Geräte")
        print(f"Entfernte Duplikate: {len(all_data) - len(deduplicated_data)}")
        
        return deduplicated_data
        
    except Exception as e:
        messagebox.showerror("Datenbankfehler", f"Fehler beim Laden der Datenbank: {str(e)}")
        return []

def update_device_location_in_db(db_path, device_address, device_type, new_lat, new_lon):
    """Aktualisiert die GPS-Koordinaten eines Geräts in der Datenbank"""
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        
        # Prüfe welche Tabellen existieren
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='device_data'")
        has_device_table = cursor.fetchone() is not None
        
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='wifi_data'")
        has_wifi_table = cursor.fetchone() is not None
        
        updated_rows = 0
        
        # Update in device_data Tabelle falls vorhanden
        if has_device_table:
            cursor.execute("""
                UPDATE device_data 
                SET latitude = ?, longitude = ?
                WHERE device_address = ? AND device_type = ?
            """, (new_lat, new_lon, device_address, device_type))
            updated_rows += cursor.rowcount
        
        # Update in wifi_data Tabelle falls WiFi-Gerät und Tabelle vorhanden
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
        print(f"Fehler beim Aktualisieren der Geräteposition: {str(e)}")
        return False

def create_wifi_map(device_data, db_filename, db_path=None):
    """Erstellt eine interaktive Karte mit WiFi- und Bluetooth-Daten"""
    if not device_data:
        messagebox.showwarning("Keine Daten", "Keine Geräte mit gültigen GPS-Koordinaten gefunden!")
        return None
    
    # Berechne Kartenzentrum (Durchschnitt aller Koordinaten)
    avg_lat = sum(device['lat'] for device in device_data) / len(device_data)
    avg_lon = sum(device['lon'] for device in device_data) / len(device_data)
    
    # Erstelle die Karte
    device_map = folium.Map(
        location=[avg_lat, avg_lon],
        zoom_start=15,
        tiles='OpenStreetMap'
    )
    
    # Erstelle eine einzige FeatureGroup für alle Marker
    all_devices_group = folium.FeatureGroup(name="Alle Geräte")
    
    # Filter-Kategorien für JavaScript-basierte Filterung
    filter_categories = {
        'wifi_open': 'WiFi Offen',
        'wifi_encrypted': 'WiFi Verschlüsselt',
        'bluetooth': 'Bluetooth',
        'bluetooth_known': 'Bluetooth ohne Unknown',
        'devices_with_movement': 'Geräte mit Bewegung',
        'signal_very_strong': 'Signal Sehr Stark (≥-50 dBm)',
        'signal_strong': 'Signal Stark (-50 bis -70 dBm)',
        'signal_medium': 'Signal Mittel (-70 bis -80 dBm)',
        'signal_weak': 'Signal Schwach (<-80 dBm)',
        'vodafone': 'Vodafone Homespot/Hotspot',
        'wifi_open_no_vodafone': 'WiFi Offen ohne Vodafone'
    }
    
    # Zähle verschiedene Gerätetypen
    wifi_open = 0
    wifi_encrypted = 0
    bluetooth_count = 0
    bluetooth_known_count = 0
    vodafone_homespot_count = 0
    wifi_open_no_vodafone = 0
    
    # Berechne Filter-Klassen für jedes Gerät im Voraus
    device_filters = []
    for device in device_data:
        filter_classes = []
        
        # Nach Gerätetyp und Verschlüsselung
        if device['type'] == "WIFI":
            if device['encryption'] == "offen":
                filter_classes.append('wifi_open')
                # WiFi Offen ohne Vodafone - nur wenn es KEIN Vodafone Homespot ist
                if not (device['name'] and ("vodafone homespot" in device['name'].lower() or "vodafone hotspot" in device['name'].lower())):
                    filter_classes.append('wifi_open_no_vodafone')
            else:
                filter_classes.append('wifi_encrypted')
        else:  # Bluetooth
            filter_classes.append('bluetooth')
            # Bluetooth ohne Unknown Device in separate Gruppe
            if device['name'] and device['name'] not in ["[Unknown Device]", "Unknown Device"]:
                filter_classes.append('bluetooth_known')
        
        # Vodafone Homespot in separate Gruppe
        if device['type'] == "WIFI" and device['name'] and ("vodafone homespot" in device['name'].lower() or "vodafone hotspot" in device['name'].lower()):
            filter_classes.append('vodafone')
        
        # Nach Bewegung - prüfe ob Gerät Bewegungsdaten hat
        if device.get('last_seen_lat') is not None and device.get('last_seen_lon') is not None:
            filter_classes.append('devices_with_movement')
        
        # Nach Signalstärke
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
            filter_classes.append('signal_weak')  # Fallback für unbekannte Signale
            
        device_filters.append(filter_classes)

    # Keine Verteilung überlappender Marker mehr, alle Marker bleiben exakt auf ihren Datenbank-Koordinaten
    device_data_distributed = device_data.copy()
    
    # Füge alle Marker zu einer einzigen Gruppe hinzu mit Filter-Klassen
    for i, device in enumerate(device_data_distributed):
        filter_classes = device_filters[i]  # Verwende vorberechnete Filter-Klassen
        device_name = device['name']
        device_address = device['address']
        device_type = device['type']
        signal = device['signal']
        encryption_info = device['encryption']
        lat = device['lat']
        lon = device['lon']
        timestamp = device['timestamp']
        # Keine Verteilung mehr, daher immer False und original = aktuelle Koordinaten
        is_distributed = False
        original_lat = lat
        original_lon = lon

        # Dynamischer Kreisradius je nach Signalstärke (dBm)
        if signal is not None:
            if signal >= -50:
                circle_radius = 10  # sehr stark
            elif signal >= -70:
                circle_radius = 20  # stark
            elif signal >= -80:
                circle_radius = 30  # mittel
            else:
                circle_radius = 40  # schwach
        else:
            circle_radius = 30  # fallback

        # Gerätenamen behandeln (falls leer)
        if not device_name or device_name.strip() == "":
            if device_type == "WIFI":
                device_name = "[Hidden Network]"
            else:
                device_name = "[Unknown Device]"

        # Hat das Gerät Bewegungsdaten?
        has_movement_data = device.get('last_seen_lat') is not None and device.get('last_seen_lon') is not None

        # Icon und Farbe basierend auf Gerätetyp und Verschlüsselung
        if device_type == "WIFI":
            if encryption_info == "offen":
                icon_color = "red"
                icon_name = "wifi"
                wifi_open += 1
                # Zähle WiFi offen ohne Vodafone separat (exkludiert Homespot UND Hotspot)
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
            # Zähle bekannte Bluetooth-Geräte (nicht "Unknown Device")
            if device_name and device_name not in ["[Unknown Device]", "Unknown Device"]:
                bluetooth_known_count += 1

        # Zähle Vodafone Homespot und Hotspot separat
        if device_type == "WIFI" and device_name and ("vodafone homespot" in device_name.lower() or "vodafone hotspot" in device_name.lower()):
            vodafone_homespot_count += 1

        # Signalstärke-Bewertung
        if signal >= -50:
            signal_text = "Sehr stark"
        elif signal >= -70:
            signal_text = "Stark"
        elif signal >= -80:
            signal_text = "Mittel"
        else:
            signal_text = "Schwach"

        # Popup-Text mit allen Informationen
        if device_type == "WIFI":
            popup_text = f"""
            <b>SSID:</b> {device_name}<br>
            <b>BSSID:</b> {device_address}<br>
            <b>Signal:</b> {signal} dBm ({signal_text})<br>
            <b>Verschlüsselung:</b> {encryption_info}<br>
            """

            # Erweiterte WiFi-Informationen hinzufügen falls verfügbar
            if device.get('frequency'):
                popup_text += f"<b>Frequenz:</b> {device['frequency']} MHz<br>"
            if device.get('channel'):
                popup_text += f"<b>Kanal:</b> {device['channel']}<br>"
            if device.get('standard'):
                popup_text += f"<b>Standard:</b> {device['standard']}<br>"
            if device.get('channel_width'):
                popup_text += f"<b>Kanalbreite:</b> {device['channel_width']} MHz<br>"
            if device.get('max_speed'):
                popup_text += f"<b>Max. Speed:</b> {device['max_speed']} Mbps<br>"
            if device.get('vendor'):
                popup_text += f"<b>Hersteller:</b> {device['vendor']}<br>"


            popup_text += f"""
            <b>Koordinaten:</b> {lat:.6f}, {lon:.6f}<br>
            <b>Zeitstempel:</b> {timestamp}
            """
            
            # Bewegungsdaten hinzufügen falls verfügbar
            if has_movement_data:
                popup_text += f"""<br><br><b>--- Bewegungsanalyse ---</b><br>
                <b>Letzte Position:</b> {device['last_seen_lat']:.6f}, {device['last_seen_lon']:.6f}<br>
                <b>Letzte Sichtung:</b> {device['last_seen_timestamp']}<br>"""
                if device.get('movement_distance'):
                    popup_text += f"<b>Bewegung:</b> {device['movement_distance']:.1f} m<br>"
        else:  # Bluetooth
            popup_text = f"""
            <b>Gerätename:</b> {device_name}<br>
            <b>MAC-Adresse:</b> {device_address}<br>
            <b>Signal:</b> {signal} dBm ({signal_text})<br>
            <b>Gerätetyp:</b> Bluetooth<br>
            <b>Geräteklasse:</b> {encryption_info}<br>
            """
            if device.get('vendor'):
                popup_text += f"<b>Hersteller:</b> {device['vendor']}<br>"
            
            
            popup_text += f"""
            <b>Koordinaten:</b> {lat:.6f}, {lon:.6f}<br>
            <b>Zeitstempel:</b> {timestamp}
            """
            
            # Bewegungsdaten hinzufügen falls verfügbar
            if has_movement_data:
                popup_text += f"""<br><br><b>--- Bewegungsanalyse ---</b><br>
                <b>Letzte Position:</b> {device['last_seen_lat']:.6f}, {device['last_seen_lon']:.6f}<br>
                <b>Letzte Sichtung:</b> {device['last_seen_timestamp']}<br>"""
                if device.get('movement_distance'):
                    popup_text += f"<b>Bewegung:</b> {device['movement_distance']:.1f} m<br>"

        # Tooltip Text mit Hinweis auf Verteilung
        tooltip_text = f"{device_name} ({signal} dBm)"
        if device_type == "BLUETOOTH":
            tooltip_text = f"[BT] {tooltip_text}"
        if device.get('vendor') and device['vendor'] != "Unknown" and not device['vendor'].startswith('Unknown ('):
            tooltip_text += f" - {device['vendor']}"
        

        # Einziger Marker mit eindeutiger ID für Filter-Zuordnung
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
            draggable=True  # Marker verschiebbar machen
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
        
        
        # Zu der einzigen Gruppe hinzufügen
        all_devices_group.add_child(marker)
        all_devices_group.add_child(circle)
    
    # Einzelne Gruppe zur Karte hinzufügen (alle Marker sind hier drin)
    device_map.add_child(all_devices_group)
    
    # Custom Layer Control für JavaScript-basierte Filter und Marker-Kontrolle hinzufügen
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
        <h4 style="margin: 0 0 10px 0; font-size: 14px;">Marker Kontrolle</h4>
        <button onclick="resetAllMarkers()" style="margin: 2px; padding: 6px 12px; font-size: 12px; background: #ff6b6b; color: white; border: none; border-radius: 3px; cursor: pointer;">Marker Zurücksetzen</button>
        <button onclick="saveAllMarkers()" style="margin: 2px; padding: 6px 12px; font-size: 12px; background: #4ecdc4; color: white; border: none; border-radius: 3px; cursor: pointer;">Positionen Speichern</button>
        <button onclick="debugMarkers()" style="margin: 2px; padding: 6px 12px; font-size: 12px; background: #9b59b6; color: white; border: none; border-radius: 3px; cursor: pointer;">Debug Marker</button>
        <br>
        <button onclick="toggleBluetoothMode()" style="margin: 5px 2px 2px 2px; padding: 6px 12px; font-size: 12px; background: #6c5ce7; color: white; border: none; border-radius: 3px; cursor: pointer;" id="bluetooth-mode-btn">BT-Nähe: Nur Bekannte</button>
        <div id="save-status" style="font-size: 11px; margin: 5px 0; min-height: 20px;"></div>
        
        <hr style="margin: 10px 0;">
        <h4 style="margin: 0 0 10px 0; font-size: 14px;">Filter</h4>
        """ + "".join([f'''
        <label style="display: block; margin: 5px 0; font-size: 12px; cursor: pointer;">
            <input type="checkbox" id="filter_{key}" onchange="toggleFilter('{key}')" style="margin-right: 8px;">
            {label}
        </label>
        ''' for key, label in filter_categories.items()]) + """
        <hr style="margin: 10px 0;">
        <button onclick="toggleAllFilters(true)" style="margin: 2px; padding: 4px 8px; font-size: 11px;">Alle Ein</button>
        <button onclick="toggleAllFilters(false)" style="margin: 2px; padding: 4px 8px; font-size: 11px;">Alle Aus</button>
    </div>
    """
    
    device_map.get_root().html.add_child(folium.Element(filter_control_html))
    
    # CSS und JavaScript für Suchfunktion und Layer-Kontrolle hinzufügen
    search_and_control_js = f"""
    <style>
    /* Verstecke alle Overlay-Layer beim Start */
    .leaflet-control-layers-overlays input[type="checkbox"] {{
        /* Alle Checkboxen sind standardmäßig nicht aktiviert */
    }}
    
    /* Verstecke alle Layer-Gruppen beim Laden */
    .leaflet-overlay-pane svg g {{
        display: none;
    }}
    
    /* Suchbox Styling */
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
    
    /* Drag-Info Styling */
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
    
    <!-- Suchbox HTML -->
    <div class="search-container">
        <input type="text" class="search-input" placeholder="SSID oder MAC suchen (z.B. 'Eduard' oder 'db:7a')..." id="searchInput">
        <div class="search-count" id="searchCount"></div>
        <div class="search-results" id="searchResults"></div>
    </div>
    
    <!-- Drag Info -->
    <div class="drag-info">
        💡 Tipp: Marker können per Drag & Drop verschoben werden
    </div>
    
    <!-- Middle-Click Info -->
    <div class="middle-click-info">
        🖱️ Mittlere Maustaste oder Rechtsklick: Marker hervorheben + SSID in Suchleiste<br>
        🔄 Nochmaliger Klick auf denselben Marker: Hervorhebung entfernen
    </div>
    
    <script>
    // Erstelle Filter-Mapping aus den Geräte-Daten
    let deviceFilterMap = new Map();
    """ + "".join([f"""
    deviceFilterMap.set('{device["address"]}', {json.dumps(device_filters[i])});""" 
    for i, device in enumerate(device_data_distributed)]) + """
    
    // Globale Variablen - Vereinfachte Search-Daten mit verteilten Koordinaten
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
    let activeFilters = new Set(); // Aktive Filter
    let allMarkers = []; // Referenzen zu allen Markern
    let allCircles = []; // Referenzen zu allen Kreisen
    let currentlyHighlightedDevice = null; // Aktuell hervorgehobenes Gerät für Toggle-Funktion
    let movementLayer = null; // Layer für die Bewegungslinie und den zweiten Marker
    let bluetoothMode = 'known'; // 'known', 'all', 'none' - Standard: nur bekannte Bluetooth-Geräte
    
    console.log('Search data loaded:', searchData.length, 'devices');
    
    // Globale Daten für Drag-Events mit verteilten Koordinaten
    let deviceDataMap = new Map();
    """ + "".join([f"""
    deviceDataMap.set('{device["address"]}', {{
        address: '{device["address"]}', 
        type: '{device["type"]}'
    }});""" for device in device_data_distributed]) + """
    
    // Filter-Funktionen
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
        // Wenn keine Filter aktiv sind, alle verstecken
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
        
        // Durchlaufe alle Geräte und prüfe Filter
        searchData.forEach((device, index) => {
            const deviceFilters = deviceFilterMap.get(device.address) || [];
            let shouldShow = deviceFilters.some(cls => activeFilters.has(cls));
            // Bluetooth-Filter-Logik
            if (device.type === 'BLUETOOTH') {
                const isUnknown = (device.name === '[Unknown Device]' || device.name === 'Unknown Device' || device.name === '' || device.name == null);
                // Nur bluetooth_known aktiv: nur bekannte Geräte
                if (activeFilters.has('bluetooth_known') && activeFilters.size === 1) {
                    shouldShow = !isUnknown;
                }
                // Nur bluetooth aktiv: alle Geräte
                else if (activeFilters.has('bluetooth') && activeFilters.size === 1) {
                    shouldShow = true;
                }
                // Beide aktiv: nur bekannte Geräte
                else if (activeFilters.has('bluetooth') && activeFilters.has('bluetooth_known') && activeFilters.size === 2) {
                    shouldShow = !isUnknown;
                }
                // bluetooth_known + weitere Filter: Unknown Devices nur zeigen, wenn ein anderer Filter (außer bluetooth_known) für dieses Gerät greift
                else if (activeFilters.has('bluetooth_known') && isUnknown) {
                    const otherActive = deviceFilters.some(cls => cls !== 'bluetooth_known' && activeFilters.has(cls));
                    shouldShow = otherActive;
                }
            }
            const marker = allMarkers[index];
            const circle = allCircles[index];
            if (shouldShow) {
                // Marker anzeigen
                if (marker && !marker._map) {
                    marker.addTo(map);
                }
                if (circle && !circle._map) {
                    circle.addTo(map);
                }
            } else {
                // Marker verstecken
                if (marker && marker._map) {
                    marker._map.removeLayer(marker);
                }
                if (circle && circle._map) {
                    circle._map.removeLayer(circle);
                }
            }
        });
    }

    // Bluetooth-Modus umschalten
    function toggleBluetoothMode() {
        const button = document.getElementById('bluetooth-mode-btn');
        
        // Umschalten zwischen den Modi
        if (bluetoothMode === 'known') {
            bluetoothMode = 'all';
            button.textContent = 'BT-Nähe: Alle';
            button.style.background = '#74b9ff';
        } else if (bluetoothMode === 'all') {
            bluetoothMode = 'none';
            button.textContent = 'BT-Nähe: Keine';
            button.style.background = '#636e72';
        } else {
            bluetoothMode = 'known';
            button.textContent = 'BT-Nähe: Nur Bekannte';
            button.style.background = '#6c5ce7';
        }
        
        // Wenn ein Gerät hervorgehoben ist, aktualisiere die Anzeige
        if (currentlyHighlightedDevice) {
            // Entferne alte Bluetooth-Marker und erstelle neue
            const currentHighlights = [...highlightedMarkers];
            clearHighlights();
            
            // Finde das Gerät und hebe es erneut hervor mit neuen Einstellungen
            const device = searchData.find(d => 
                d.address === currentlyHighlightedDevice.address && 
                d.type === currentlyHighlightedDevice.type
            );
            
            if (device) {
                highlightDeviceByObject(device, 'Bluetooth-Modus geändert');
            }
        }
        
        console.log(`Bluetooth-Modus geändert auf: ${bluetoothMode}`);
    }
    
    // Warte bis die Karte geladen ist
    document.addEventListener('DOMContentLoaded', function() {
        console.log('DOM loaded, setting up search...');
        
        // Finde die Leaflet-Karte
        setTimeout(function() {
            // Karten-Referenz finden - bessere Methode
            if (typeof window.map_1 !== 'undefined') {
                map = window.map_1;
            } else {
                // Fallback: Suche nach Leaflet-Karten-Instanz
                let mapElements = document.querySelectorAll('.leaflet-container');
                if (mapElements.length > 0) {
                    let mapId = mapElements[0].id;
                    if (window[mapId]) {
                        map = window[mapId];
                    }
                }
            }
            
            console.log('Map found:', map ? 'Yes' : 'No');
            
            // Sammle alle Marker und Kreise für die Filter-Kontrolle
            map.eachLayer(function(layer) {
                if (layer instanceof L.Marker) {
                    allMarkers.push(layer);
                } else if (layer instanceof L.Circle) {
                    allCircles.push(layer);
                }
            });

            // Alle Marker wirklich als draggable setzen (Leaflet)
            allMarkers.forEach(function(marker, idx) {
                // Speichere Gerätedaten direkt am Marker
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

            // Initial alle Marker verstecken (da keine Filter aktiv)
            updateMarkerVisibility();

            setupSearch();
            setupDragHandlers();

            // Drag-End-Event für alle Marker im Layer registrieren
            map.eachLayer(function(layer) {
                if (layer instanceof L.Marker && layer.options.draggable && layer.dragging) {
                    layer.on('dragend', function(e) {
                        var newLat = e.target.getLatLng().lat;
                        var newLon = e.target.getLatLng().lng;
                        var address = e.target.deviceAddress;
                        var typ = e.target.deviceType;
                        if (address && typ) {
                            console.log('DragEnd (global): Device direkt am Marker:', address, typ, newLat, newLon);
                            updateDeviceLocation(address, typ, newLat, newLon);
                            console.log('pendingUpdates:', pendingUpdates.length);
                        } else {
                            console.warn('DragEnd (global): Keine Gerätedaten am Marker!', newLat, newLon);
                        }
                    });
                }
            });
        }, 1500);
    });
    
    // Setup Drag-Event und Click-Handler für alle Marker
    function setupDragHandlers() {
        setTimeout(function() {
            // Finde alle Marker auf der Karte
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
                        // Fallback: Suche nach Gerät anhand der Koordinaten
                        if (!device) {
                            device = searchData.find(d => Math.abs(d.lat - newLat) < 0.0001 && Math.abs(d.lon - newLon) < 0.0001);
                        }
                        if (device) {
                            console.log('DragEnd: Device gefunden:', device.address, device.type, newLat, newLon);
                            updateDeviceLocation(device.address, device.type, newLat, newLon);
                            console.log('pendingUpdates:', pendingUpdates.length);
                        } else {
                            console.warn('DragEnd: Kein Gerät zu Marker gefunden!', newLat, newLon);
                        }
                    });
                    
                    // Middle-Click Handler für Highlight-Funktion - Verbesserte Version
                    layer.on('mousedown', function(e) {
                        console.log('Mouse button pressed:', e.originalEvent.button);
                        
                        // Prüfe auf mittlere Maustaste (Button 1) oder Rechtsklick als Alternative
                        if (e.originalEvent.button === 1 || e.originalEvent.button === 2) {
                            e.originalEvent.preventDefault(); // Verhindere Standard-Verhalten
                            e.originalEvent.stopPropagation();
                            
                            console.log('Middle/Right click detected on marker');
                            
                            // Extrahiere Geräte-Info aus Popup
                            var popupContent = e.target.getPopup().getContent();
                            var addressMatch = popupContent.match(/(?:BSSID|MAC-Adresse):<\/b>\s*([A-Fa-f0-9:]{17})/);
                            var ssidMatch = popupContent.match(/<b>(?:SSID|Gerätename):<\/b>\s*([^<]+)/);
                            
                            console.log('Popup content:', popupContent);
                            console.log('Address match:', addressMatch);
                            console.log('SSID match:', ssidMatch);
                            
                            if (addressMatch && ssidMatch) {
                                var deviceAddress = addressMatch[1];
                                var deviceName = ssidMatch[1].trim();
                                
                                console.log('Found device:', deviceAddress, deviceName);
                                
                                // Finde das Gerät in den searchData
                                var device = searchData.find(d => d.address === deviceAddress);
                                if (device) {
                                    console.log('Device found in searchData, highlighting...');
                                    
                                    // Highlight das Gerät wie bei der Suche
                                    highlightDeviceByObject(device, deviceName);
                                    
                                    // Setze SSID in die Suchleiste (bereinige Sonderzeichen)
                                    var searchInput = document.getElementById('searchInput');
                                    if (searchInput && device.type === 'WIFI') {
                                        // Entferne HTML-Entities und bereinige String
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
                                    
                                    // Erfolgs-Feedback
                                    console.log('✅ Marker highlighted successfully!');
                                } else {
                                    console.warn('Device not found in searchData');
                                }
                            } else {
                                console.warn('Could not extract device info from popup');
                            }
                            
                            return false; // Verhindere weitere Event-Propagation
                        }
                    });
                    
                    // Zusätzlicher Context-Menu Handler als Alternative
                    layer.on('contextmenu', function(e) {
                        e.originalEvent.preventDefault();
                        console.log('Context menu (right click) on marker');
                        
                        // Triggere den gleichen Highlight-Prozess
                        var popupContent = e.target.getPopup().getContent();
                        var addressMatch = popupContent.match(/(?:BSSID|MAC-Adresse):<\/b>\s*([A-Fa-f0-9:]{17})/);
                        var ssidMatch = popupContent.match(/<b>(?:SSID|Gerätename):<\/b>\s*([^<]+)/);
                        
                        if (addressMatch && ssidMatch) {
                            var deviceAddress = addressMatch[1];
                            var deviceName = ssidMatch[1].trim();
                            var device = searchData.find(d => d.address === deviceAddress);
                            
                            if (device) {
                                highlightDeviceByObject(device, 'Rechtsklick');
                                
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
                                
                                // Zeige Bestätigung
                                alert(`✅ Marker hervorgehoben!\nSSID: ${deviceName}\nBSSID: ${deviceAddress}`);
                            }
                        }
                        
                        return false;
                    });
                }
            });
            console.log('Drag and click handlers setup complete');
        }, 2000);
    }
    
    // Suchfunktion einrichten
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
            
            // Suche in den Daten: SSID ODER MAC-Adresse (bzw. address)
            const results = searchData.filter(device => 
                (device.name.toLowerCase().includes(query) || device.address.toLowerCase().includes(query)) && device.type === 'WIFI'
            );
            
            // Ergebnisse anzeigen
            displaySearchResults(results, query);
        });
        
        // Beim Löschen der Suchleiste auch Hervorhebungen entfernen
        searchInput.addEventListener('keyup', function(e) {
            if (e.key === 'Escape' || (e.key === 'Backspace' && this.value.length === 0)) {
                clearHighlights();
                searchResults.style.display = 'none';
                searchCount.textContent = '';
            }
        });
        
        // Klick außerhalb schließt Ergebnisse
        document.addEventListener('click', function(e) {
            if (!e.target.closest('.search-container')) {
                searchResults.style.display = 'none';
            }
        });
    }
    
    // Suchergebnisse anzeigen
    function displaySearchResults(results, query) {
        const searchResults = document.getElementById('searchResults');
        const searchCount = document.getElementById('searchCount');
        
        if (results.length === 0) {
            searchResults.innerHTML = '<div class="search-result-item">Keine Ergebnisse gefunden</div>';
            searchCount.textContent = '0 Ergebnisse';
            clearHighlights(); // Hervorhebung löschen wenn nichts gefunden
            searchResults.style.display = 'block';
        } else {
            searchResults.innerHTML = results.map((device, index) => {
                const signalText = device.signal >= -50 ? 'Sehr stark' : 
                                 device.signal >= -70 ? 'Stark' : 
                                 device.signal >= -80 ? 'Mittel' : 'Schwach';
                
                return `<div class="search-result-item" onclick="highlightDevice(${index}, '${query}')">
                    <strong>${device.name}</strong><br>
                    <small>${device.address} | ${device.signal} dBm (${signalText}) | ${device.encryption}</small>
                </div>`;
            }).join('');
            
            searchCount.textContent = `${results.length} Ergebnis${results.length !== 1 ? 'se' : ''}`;
            searchResults.style.display = 'block';
            
            // Automatisch hervorheben, wenn nur ein Ergebnis gefunden wurde
            if (results.length === 1) {
                setTimeout(() => {
                    highlightDeviceByObject(results[0], query);
                }, 100);
            }
        }
        
        // Speichere aktuelle Suchergebnisse
        window.currentSearchResults = results;
    }
    
    // Gerät auf Karte hervorheben (über Index aus Suchergebnissen)
    function highlightDevice(index, query) {
        if (!map || !window.currentSearchResults) return;
        
        const device = window.currentSearchResults[index];
        highlightDeviceByObject(device, query);
    }
    
    // Gerät auf Karte hervorheben (direkt über Device-Objekt)
    function highlightDeviceByObject(device, query) {
        if (!map || !device) return;
        
        // Prüfe ob das gleiche Gerät bereits hervorgehoben ist (Toggle-Funktion)
        if (currentlyHighlightedDevice && 
            currentlyHighlightedDevice.address === device.address && 
            currentlyHighlightedDevice.type === device.type) {
            
            // Entferne Hervorhebung (Toggle OFF)
            clearHighlights();
            currentlyHighlightedDevice = null;
            
            // Feedback für Benutzer
            console.log(`🔄 Hervorhebung für ${device.name} (${device.address}) entfernt`);
            
            // Optional: Kurze Meldung anzeigen
            const searchInput = document.getElementById('searchInput');
            if (searchInput) {
                const originalPlaceholder = searchInput.placeholder;
                searchInput.placeholder = `✅ ${device.name} abgewählt`;
                setTimeout(() => {
                    searchInput.placeholder = originalPlaceholder;
                }, 2000);
            }
            
            return;
        }
        
        // Alte Highlights entfernen
        clearHighlights();
        
        // Speichere aktuell hervorgehobenes Gerät
        currentlyHighlightedDevice = {
            address: device.address,
            type: device.type,
            name: device.name
        };
        
        // Zur Position zoomen
        map.setView([device.lat, device.lon], 18);
        
        // Finde den originalen Marker für dieses Gerät
        let originalMarkerIndex = -1;
        for (let i = 0; i < searchData.length; i++) {
            if (searchData[i].address === device.address && searchData[i].type === device.type) {
                originalMarkerIndex = i;
                break;
            }
        }
        
        // Stelle sicher, dass der originale Marker sichtbar ist (auch wenn Filter ihn versteckt haben)
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
        
        // Zusätzlicher goldener Highlight-Ring (ohne neuen Marker)
        const highlightCircle = L.circle([device.lat, device.lon], {
            color: 'gold',
            fillColor: 'gold',
            fillOpacity: 0.3,
            radius: 80,
            weight: 4
        }).addTo(map);
        
        // Pulsierender äußerer Ring
        const pulseCircle = L.circle([device.lat, device.lon], {
            color: 'orange',
            fillColor: 'orange',
            fillOpacity: 0.1,
            radius: 120,
            weight: 2
        }).addTo(map);
        
        // 50m Radius für Bluetooth-Suche (nur bei WiFi-Geräten)
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
            
            // Finde und zeige alle Bluetooth-Geräte im 50m Umkreis
            const nearbyBluetoothDevices = findBluetoothDevicesInRadius(device.lat, device.lon, 50);
            bluetoothMarkers = showNearbyBluetoothDevices(nearbyBluetoothDevices);
            
            // Info über gefundene Bluetooth-Geräte anzeigen (verzögert)
            if (nearbyBluetoothDevices.length > 0 && bluetoothMode !== 'none') {
                setTimeout(() => {
                    let displayedCount = bluetoothMarkers.length / 3; // 3 Elemente pro Gerät (Marker, Circle, Label)
                    let modeText = bluetoothMode === 'known' ? ' (nur bekannte)' : 
                                  bluetoothMode === 'all' ? ' (alle)' : '';
                    console.log(`📱 ${displayedCount} von ${nearbyBluetoothDevices.length} Bluetooth-Geräte im 50m Umkreis angezeigt${modeText}`);
                }, 1000);
            }
        }
        
        // Speichere Highlights zum späteren Entfernen
        highlightedMarkers.push(highlightCircle, pulseCircle);
        if (searchRadius) highlightedMarkers.push(searchRadius);
        highlightedMarkers.push(...bluetoothMarkers);
        
        // Suchergebnisse ausblenden falls geöffnet
        document.getElementById('searchResults').style.display = 'none';
        
        // Animation für Aufmerksamkeit
        setTimeout(() => {
            if (highlightCircle._map) {
                highlightCircle.setStyle({fillOpacity: 0.1});
                setTimeout(() => {
                    if (highlightCircle._map) highlightCircle.setStyle({fillOpacity: 0.3});
                }, 500);
            }
        }, 300);
    }
    
    // Highlights entfernen
    function clearHighlights() {
        highlightedMarkers.forEach(marker => {
            if (marker._map) {
                map.removeLayer(marker);
            }
        });
        highlightedMarkers = [];
        // Setze aktuell hervorgehobenes Gerät zurück wenn Highlights manuell gelöscht werden
        if (currentlyHighlightedDevice) {
            console.log(`🔄 Hervorhebung für ${currentlyHighlightedDevice.name} manuell entfernt`);
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
        debugInfo += `Sichtbare Marker: ${visibleCount}\\n`;
        debugInfo += `Versteckte Marker: ${hiddenCount}\\n`;
        debugInfo += `Hervorgehobene Marker: ${highlightedCount}\\n`;
        debugInfo += `Gesamt Marker: ${allMarkers.length}\\n`;
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
    
    // Berechne Entfernung zwischen zwei GPS-Koordinaten (Haversine Formel)
    function calculateDistance(lat1, lon1, lat2, lon2) {
        const R = 6371e3; // Erdradius in Metern
        const φ1 = lat1 * Math.PI/180;
        const φ2 = lat2 * Math.PI/180;
        const Δφ = (lat2-lat1) * Math.PI/180;
        const Δλ = (lon2-lon1) * Math.PI/180;

        const a = Math.sin(Δφ/2) * Math.sin(Δφ/2) +
                  Math.cos(φ1) * Math.cos(φ2) *
                  Math.sin(Δλ/2) * Math.sin(Δλ/2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return R * c; // Entfernung in Metern
    }
    
    // Finde alle Bluetooth-Geräte im angegebenen Radius
    function findBluetoothDevicesInRadius(centerLat, centerLon, radiusMeters) {
        return searchData.filter(device => {
            if (device.type !== 'BLUETOOTH') return false;
            
            const distance = calculateDistance(centerLat, centerLon, device.lat, device.lon);
            return distance <= radiusMeters;
        });
    }
    
    // Zeige Bluetooth-Geräte in der Nähe an
    function showNearbyBluetoothDevices(bluetoothDevices) {
        const bluetoothMarkers = [];
        
        // Prüfe Bluetooth-Modus und filtere entsprechend
        let filteredDevices = bluetoothDevices;
        
        if (bluetoothMode === 'none') {
            // Keine Bluetooth-Geräte anzeigen
            return bluetoothMarkers;
        } else if (bluetoothMode === 'known') {
            // Nur bekannte Bluetooth-Geräte (ohne "Unknown")
            filteredDevices = bluetoothDevices.filter(device => {
                const deviceName = device.name || '';
                return deviceName !== '[Unknown Device]' && 
                       deviceName !== 'Unknown Device' && 
                       deviceName !== '' && 
                       deviceName !== null &&
                       !deviceName.toLowerCase().includes('unknown');
            });
        }
        // Bei bluetoothMode === 'all' werden alle Geräte angezeigt (keine Filterung)
        
        filteredDevices.forEach((device, index) => {
            // Bluetooth-Marker mit spezieller Farbe
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
            
            // Popup für Bluetooth-Gerät
            const signalText = device.signal >= -50 ? 'Sehr stark' : 
                             device.signal >= -70 ? 'Stark' : 
                             device.signal >= -80 ? 'Mittel' : 'Schwach';
            
            btMarker.bindPopup(`
                <b>📱 BLUETOOTH: ${device.name}</b><br>
                <b>MAC:</b> ${device.address}<br>
                <b>Signal:</b> ${device.signal} dBm (${signalText})<br>
                <b>Klasse:</b> ${device.encryption}<br>
                <small><i>Automatisch angezeigt (50m Radius)</i></small>
            `);
            
            // Name-Label über dem Bluetooth-Marker
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
            
            // Kleiner Kreis um Bluetooth-Gerät
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
    
    // Funktion zum Aktualisieren der Geräteposition in der Datenbank
    function updateDeviceLocation(deviceAddress, deviceType, newLat, newLon) {
        // Zeige Bestätigung mit den neuen Koordinaten
        const confirmed = confirm(
            `Gerät ${deviceAddress} (${deviceType}) verschieben?\n\n` +
            `Neue Koordinaten:\n` +
            `Latitude: ${newLat.toFixed(6)}\n` +
            `Longitude: ${newLon.toFixed(6)}\n\n` +
            `Änderung wird für Datenbank-Update vorgemerkt.`
        );
        
        if (!confirmed) {
            // Seite neu laden um Marker zurückzusetzen
            location.reload();
            return;
        }
        
        // Füge Update zur Liste hinzu
        pendingUpdates.push({
            address: deviceAddress,
            type: deviceType,
            latitude: newLat,
            longitude: newLon,
            timestamp: new Date().toISOString()
        });
        
        // Zeige Erfolg-Meldung mit Batch-Option
        const batchUpdate = pendingUpdates.length > 1;
        let message = `📍 Position vorgemerkt!\n\n` +
            `Gerät: ${deviceAddress}\n` +
            `Typ: ${deviceType}\n` +
            `Neue Lat: ${newLat.toFixed(6)}\n` +
            `Neue Lon: ${newLon.toFixed(6)}\n\n`;
        
        if (batchUpdate) {
            message += `📝 ${pendingUpdates.length} Änderungen vorgemerkt.\n` +
                      `Beim Schließen der Anwendung werden alle\n` +
                      `Änderungen in die Datenbank geschrieben.`;
        } else {
            message += `💾 Beim Schließen der Anwendung wird die\n` +
                      `Änderung in die Datenbank geschrieben.`;
        }
        
        alert(message);
        
        // Update auch in den searchData für Konsistenz
        const dataIndex = searchData.findIndex(d => d.address === deviceAddress && d.type === deviceType);
        if (dataIndex !== -1) {
            searchData[dataIndex].lat = newLat;
            searchData[dataIndex].lon = newLon;
        }
        
        console.log(`Position queued for update: ${deviceAddress} -> ${newLat}, ${newLon}`);
        console.log(`Total pending updates: ${pendingUpdates.length}`);
    }
    
    // Beim Schließen der Seite Updates speichern
    window.addEventListener('beforeunload', function(e) {
        if (pendingUpdates.length > 0) {
            // Speichere Updates in localStorage für Python-Verarbeitung
            localStorage.setItem('pendingLocationUpdates', JSON.stringify(pendingUpdates));
            
            const message = `${pendingUpdates.length} Positionsänderung(en) werden gespeichert...`;
            e.returnValue = message;
            return message;
        }
    });

    // Funktion zum Zurücksetzen aller Marker auf ursprüngliche Positionen
    function resetAllMarkers() {
        if (!confirm('Alle Marker auf ursprüngliche Positionen zurücksetzen?')) return;
        
        // Seite neu laden um alle Marker zurückzusetzen
        location.reload();
    }

    // Funktion zum Speichern aller vorgemerkten Positionsänderungen
    function saveAllMarkers() {
        if (pendingUpdates.length === 0) {
            alert('Keine Positionsänderungen vorgemerkt!');
            return;
        }
        // Übersicht der geänderten Marker anzeigen
        let msg = `Folgende Marker wurden verschoben und werden gespeichert:\n\n`;
        pendingUpdates.forEach((upd, idx) => {
            msg += `${idx+1}. ${upd.address} (${upd.type})\n   Neue Lat: ${upd.latitude.toFixed(6)}\n   Neue Lon: ${upd.longitude.toFixed(6)}\n\n`;
        });
        msg += `\nJetzt wirklich in die Datenbank schreiben?`;
        if (!confirm(msg)) return;

        // Speichere Updates in localStorage und als Datei
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

        // Status anzeigen
        const statusEl = document.getElementById('save-status');
        statusEl.innerHTML = `✅ ${pendingUpdates.length} Position(en) exportiert!`;
        statusEl.style.color = 'green';

        setTimeout(() => {
            alert(`✅ Updates wurden als JSON-Datei heruntergeladen!\n\n` +
                  `Legen Sie die Datei "wifi_scanner_updates.json" in den Programmordner.\n\n` +
                  `Die Updates werden beim nächsten Programmstart automatisch in die Datenbank geschrieben.`);
            pendingUpdates = [];
            statusEl.innerHTML = '';
        }, 1500);
    }
    </script>
    """
    
    device_map.get_root().html.add_child(folium.Element(search_and_control_js))
    
    # Legende hinzufügen
    total_wifi = wifi_open + wifi_encrypted
    total_devices = len(device_data)
    
    legend_html = f"""
    <div id="legend-info-box" style="position: fixed; 
                top: 10px; right: 10px; width: 230px; max-height: 340px;
                background-color: white; border:1.5px solid #888; z-index:9999; 
                font-size:12px; padding: 7px 8px; overflow-y: auto; box-shadow: 0 1px 7px rgba(0,0,0,0.13); border-radius: 7px;">
    <h4 style='margin:0 0 6px 0; font-size:13px; font-weight:bold;'>WiFi & Bluetooth Scanner</h4>
    <p style='margin:2px 0;'><i class="fa fa-wifi" style="color:red"></i> WiFi Offen: {wifi_open}</p>
    <p style='margin:2px 0;'><i class="fa fa-lock" style="color:green"></i> WiFi Verschlüsselt: {wifi_encrypted}</p>
    <p style='margin:2px 0;'><i class="fa fa-bluetooth" style="color:blue"></i> Bluetooth: {bluetooth_count}</p>
    <p style='margin:2px 0;'><i class="fa fa-bluetooth" style="color:darkblue"></i> Bluetooth ohne Unknown: {bluetooth_known_count}</p>
    <p style='margin:2px 0;'><i class="fa fa-wifi" style="color:orange"></i> Vodafone Homespot/Hotspot: {vodafone_homespot_count}</p>
    <p style='margin:2px 0;'><i class="fa fa-star" style="color:purple"></i> WiFi Offen ohne Vodafone: {wifi_open_no_vodafone}</p>
    <p style='margin:2px 0;'><b>Gesamt:</b> {total_devices} Geräte</p>
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
                        info += `<b>Typ:</b> ${device.type}<br>`;
                        info += `<b>Signal:</b> ${device.signal} dBm<br>`;
                        document.getElementById('legend-selected-device').innerHTML = info;
                        // Show highlight button
                        document.getElementById('legend-highlight-btn').style.display = 'block';

                        // --- NEU: Bewegungsdaten anzeigen ---
                        if (movementLayer) {
                            map.removeLayer(movementLayer);
                            movementLayer = null;
                        }

                        if (device.last_seen_lat && device.last_seen_lon) {
                            movementLayer = L.layerGroup().addTo(map);

                            // Marker für die letzte Position
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
                            lastSeenMarker.bindPopup(`<b>Letzte Position von:</b><br>${device.name}<br>${device.last_seen_timestamp}`);

                            // Linie zwischen den beiden Punkten
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

def process_pending_location_updates(db_path):
    """Verarbeitet ausstehende Standort-Updates aus temporärer Datei"""
    import json, os, tempfile
    try:
        print("Prüfe auf ausstehende Standort-Updates...")
        # Unterstütze beide Speicherorte
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
            print("Keine Positions-Updates gefunden.")
            return True
        with open(found_file, 'r', encoding='utf-8') as f:
            pending_updates = json.load(f)
        if not pending_updates:
            os.remove(found_file)
            print("Update-Datei ist leer.")
            return True
        print(f"Gefunden: {len(pending_updates)} ausstehende Positionsänderungen")
        successful_updates = 0
        for update in pending_updates:
            try:
                success = update_device_location_in_db(
                    db_path,
                    update['address'],
                    update['type'],
                    update['latitude'],
                    update['longitude']
                )
                if success:
                    successful_updates += 1
                    print(f"✅ Updated {update['address']} -> {update['latitude']:.6f}, {update['longitude']:.6f}")
                else:
                    print(f"❌ Failed to update {update['address']}")
            except Exception as e:
                print(f"❌ Error updating {update['address']}: {str(e)}")
        print(f"Erfolgreich aktualisiert: {successful_updates}/{len(pending_updates)} Geräte")
        os.remove(found_file)
        if successful_updates > 0:
            print("Positionsänderungen wurden in die Datenbank übernommen!")
        return True
    except Exception as e:
        print(f"Fehler beim Verarbeiten der Updates: {str(e)}")
        return False

def main():
    """Hauptfunktion des Programms"""
    print("WiFi & Bluetooth Scanner Karten-Viewer")
    print("=" * 40)
    
    # Datenbankdatei auswählen
    db_path = select_database_file()
    
    if not db_path:
        print("Keine Datei ausgewählt. Programm wird beendet.")
        return
    
    print(f"Lade Datenbank: {os.path.basename(db_path)}")
    
    # Verarbeite ausstehende Standort-Updates
    process_pending_location_updates(db_path)
    
    # Geräte-Daten laden (WiFi + Bluetooth)
    device_data = load_wifi_data(db_path)
    
    if not device_data:
        print("Keine gültigen Geräte-Daten gefunden.")
        return
    
    # Zähle verschiedene Gerätetypen
    wifi_count = sum(1 for device in device_data if device['type'] == 'WIFI')
    bluetooth_count = sum(1 for device in device_data if device['type'] == 'BLUETOOTH')
    
    print(f"Gefunden: {len(device_data)} Geräte mit GPS-Daten")
    print(f"  - WiFi-Netzwerke: {wifi_count}")
    print(f"  - Bluetooth-Geräte: {bluetooth_count}")
    
    # Zeige Vendor-Statistik
    vendors = {}
    for device in device_data:
        vendor = device.get('vendor', 'Unknown')
        if vendor != 'Unknown' and not vendor.startswith('Unknown ('):
            vendors[vendor] = vendors.get(vendor, 0) + 1
    
    if vendors:
        print(f"\nTop Hersteller:")
        sorted_vendors = sorted(vendors.items(), key=lambda x: x[1], reverse=True)[:5]
        for vendor, count in sorted_vendors:
            print(f"  - {vendor}: {count} Geräte")
    
    # Karte erstellen
    db_filename = os.path.basename(db_path)
    device_map = create_wifi_map(device_data, db_filename, db_path)
    
    if device_map:
        # Temporäre HTML-Datei erstellen
        temp_file = tempfile.NamedTemporaryFile(delete=False, suffix='.html')
        map_path = temp_file.name
        temp_file.close()
        
        # Karte speichern
        device_map.save(map_path)
        
        print(f"Karte wurde erstellt: {map_path}")
        print("Öffne Karte im Browser...")
        
        # Karte im Browser öffnen
        webbrowser.open('file://' + os.path.realpath(map_path))
        
        input("Drücke Enter zum Beenden...")
        
        # Temporäre Datei löschen
        try:
            os.unlink(map_path)
        except:
            pass

if __name__ == "__main__":
    main()

