#!/usr/bin/env python3
"""
Automated Translation Script for Python GUI files
Translates German strings to English in start_combine_dbs.py and start_plot_gui.py
"""

import re
import shutil
from pathlib import Path

# Translation dictionary
TRANSLATIONS = {
    # Window titles
    "WiFi/Bluetooth Datenbank Combiner": "WiFi/Bluetooth Database Combiner",
    "Datenbank Zusammenführung": "Database Merger",

    # UI Labels
    "Haupt-Datenbank (Ziel):": "Main Database (Target):",
    "Quell-Datenbanken (hinzufügen):": "Source Databases (to add):",
    "Zusammenführungs-Optionen": "Merge Options",

    # Buttons
    "Auswählen": "Select",
    "DB hinzufügen": "Add DB",
    "Ausgewählte entfernen": "Remove Selected",
    "Alle entfernen": "Remove All",
    "🔍 Analyse starten": "🔍 Start Analysis",
    "🧹 DB bereinigen": "🧹 Clean DB",
    "🚀 Zusammenführen": "🚀 Merge",
    "❌ Beenden": "❌ Exit",

    # Checkboxes
    "Backup der Haupt-DB erstellen": "Create backup of main DB",
    "Bessere Signalstärke bevorzugen": "Prefer better signal strength",
    "Koordinaten aktualisieren bei besserem Signal": "Update coordinates with better signal",

    # Status messages
    "Bereit zum Start...": "Ready to start...",
    "Verarbeite...": "Processing...",

    # Dialog titles
    "Fehler": "Error",
    "Warnung": "Warning",
    "Erfolg": "Success",
    "Bestätigung": "Confirmation",
    "Aufräumen": "Cleanup",

    # File dialog titles
    "Haupt-Datenbank auswählen": "Select Main Database",
    "Quell-Datenbank(en) auswählen": "Select Source Database(s)",

    # Dialog messages
    "Die Haupt-DB kann nicht als Quell-DB verwendet werden!": "The main DB cannot be used as source DB!",
    "Bitte wählen Sie zuerst eine Haupt-Datenbank aus!": "Please select a main database first!",
    "Bitte fügen Sie mindestens eine Quell-Datenbank hinzu oder nutzen Sie 'DB bereinigen' für eine einzelne Datenbank!": "Please add at least one source database or use 'Clean DB' for a single database!",
    "Backup fehlgeschlagen. Trotzdem fortfahren?": "Backup failed. Continue anyway?",
    "Bitte wählen Sie zuerst eine Datenbank zum Bereinigen aus!": "Please select a database to clean first!",
    "Möchten Sie die alten/leeren Tabellen löschen?": "Do you want to delete old/empty tables?",
    "Bitte wählen Sie zuerst eine Datenbank zum Reparieren aus!": "Please select a database to repair first!",

    # Log messages - patterns
    "=== ANALYSE GESTARTET ===": "=== ANALYSIS STARTED ===",
    "=== ANALYSE GESTARTET (nur Haupt-DB) ===": "=== ANALYSIS STARTED (main DB only) ===",
    "=== ANALYSE ABGESCHLOSSEN ===": "=== ANALYSIS COMPLETED ===",
    "=== ZUSAMMENFÜHRUNG GESTARTET ===": "=== MERGE STARTED ===",
    "=== ZUSAMMENFÜHRUNG ABGESCHLOSSEN ===": "=== MERGE COMPLETED ===",
    "=== DATENBANK-BEREINIGUNG GESTARTET ===": "=== DATABASE CLEANUP STARTED ===",
    "=== BEREINIGUNG ABGESCHLOSSEN ===": "=== CLEANUP COMPLETED ===",
    "=== DATENBANK-REPARATUR GESTARTET ===": "=== DATABASE REPAIR STARTED ===",
    "=== REPARATUR ABGESCHLOSSEN ===": "=== REPAIR COMPLETED ===",

    # Log messages - specific
    "Analysiere Haupt-Datenbank...": "Analyzing main database...",
    "Lade existierende Geräte aus Haupt-DB...": "Loading existing devices from main DB...",
    "Prüfe und entferne Duplikate nach Zusammenführung...": "Checking and removing duplicates after merge...",
    "Verschiebe WiFi-Daten von device_data zu wifi_data...": "Moving WiFi data from device_data to wifi_data...",
    "Entferne Duplikate in wifi_data...": "Removing duplicates in wifi_data...",
    "Entferne Duplikate in device_data...": "Removing duplicates in device_data...",
    "Prüfe und erstelle moderne Tabellenstrukturen...": "Checking and creating modern table structures...",
    "Prüfe und füge Bewegungsverfolgung-Spalten hinzu...": "Checking and adding movement tracking columns...",
    "Prüfe und repariere Standard-Spalten...": "Checking and repairing standard columns...",
    "Erstelle Performance-Indizes...": "Creating performance indexes...",

    # Log messages - with variables
    "Haupt-DB ausgewählt:": "Main DB selected:",
    "Quell-DB hinzugefügt:": "Source DB added:",
    "Quell-DB entfernt:": "Source DB removed:",
    "Alle Quell-DBs entfernt": "All source DBs removed",
    "Fehler beim Analysieren von": "Error analyzing",
    "Haupt-DB:": "Main DB:",
    "Device-Einträge,": "device entries,",
    "WiFi-Einträge": "WiFi entries",
    "Tabellen:": "Tables:",
    "Analysiere Quell-DB": "Analyzing source DB",
    "Gesamt zu verarbeiten:": "Total to process:",
    "Fehler beim Laden von": "Error loading",
    "Gerät bewegt:": "Device moved:",
    "m entfernt, Hauptposition aktualisiert": "m away, main position updated",
    "m entfernt, Last Seen aktualisiert": "m away, last seen updated",
    "Backup erstellt:": "Backup created:",
    "Fehler beim Erstellen des Backups:": "Error creating backup:",
    "Tabellenstruktur in": "Table structure in",
    "aktualisiert.": "updated.",
    "Fehler beim Aktualisieren der Tabellenstruktur in": "Error updating table structure in",
    "Gefunden:": "Found:",
    "existierende Geräte": "existing devices",
    "Verarbeite": "Processing",
    "Verarbeitet:": "Processed:",
    "Geräte": "devices",
    "Neu hinzugefügt:": "Newly added:",
    "Aktualisiert (besseres Signal):": "Updated (better signal):",
    "Bewegung erkannt - Hauptposition aktualisiert:": "Movement detected - main position updated:",
    "Bewegung erkannt - Last Seen aktualisiert:": "Movement detected - last seen updated:",
    "Übersprungen (zu nahe < 100m):": "Skipped (too close < 100m):",
    "Übersprungen (schlechteres Signal):": "Skipped (worse signal):",
    "Übersprungen (besseres existiert):": "Skipped (better exists):",
    "Duplikate entfernt:": "Duplicates removed:",
    "WiFi": "WiFi",
    "Bluetooth": "Bluetooth",
    "Gerätebewegungen erkannt:": "Device movements detected:",
    "Fehler bei der Zusammenführung:": "Error during merge:",
    "Fehler bei der Bereinigung:": "Error during cleanup:",
    "Fehler bei der Reparatur:": "Error during repair:",
    "Vorhandene Tabellen:": "Existing tables:",
    "WiFi-Duplikate entfernt:": "WiFi duplicates removed:",
    "Bluetooth-Duplikate entfernt:": "Bluetooth duplicates removed:",
    "Tabellen erstellt:": "Tables created:",
    "Spalten hinzugefügt:": "Columns added:",
    "Tabellen analysiert:": "Tables analyzed:",
    "Spalte": "Column",
    "zu wifi_data hinzugefügt": "added to wifi_data",
    "zu device_data hinzugefügt": "added to device_data",
    "Warnung: Konnte Spalte": "Warning: Could not add column",
    "nicht zu wifi_data hinzufügen:": "to wifi_data:",
    "nicht zu device_data hinzufügen:": "to device_data:",
    "Fehler beim Reparieren von wifi_data:": "Error repairing wifi_data:",
    "Fehler beim Reparieren von device_data:": "Error repairing device_data:",
    "Index": "Index",
    "erstellt/überprüft": "created/verified",
    "Warnung: Konnte Index": "Warning: Could not create index",
    "nicht erstellen:": ":",

    # Comments (Docstrings)
    "Erstellt die GUI für die Datenbankzusammenführung": "Creates the GUI for database merging",
    "Fügt eine Nachricht zum Log hinzu": "Adds a message to the log",
    "Wählt die Haupt-Datenbank aus": "Selects the main database",
    "Fügt eine Quell-Datenbank hinzu": "Adds a source database",
    "Entfernt ausgewählte Quell-Datenbank": "Removes selected source database",
    "Entfernt alle Quell-Datenbanken": "Removes all source databases",
    "Ermittelt Informationen über die Tabellen in der Datenbank": "Retrieves information about tables in the database",
}

def translate_file(input_path, output_path=None, dry_run=False):
    """
    Translates German strings to English in a Python file

    Args:
        input_path: Path to input file
        output_path: Path to output file (if None, overwrites input)
        dry_run: If True, only shows what would be changed
    """
    input_file = Path(input_path)
    if not input_file.exists():
        print(f"❌ File not found: {input_path}")
        return

    # Read file
    with open(input_file, 'r', encoding='utf-8') as f:
        content = f.read()

    original_content = content
    changes_made = []

    # Apply translations
    for german, english in TRANSLATIONS.items():
        if german in content:
            count = content.count(german)
            content = content.replace(german, english)
            changes_made.append(f"  • '{german}' → '{english}' ({count}x)")

    # Show changes
    if changes_made:
        print(f"\n📝 Changes in {input_file.name}:")
        for change in changes_made[:20]:  # Show first 20 changes
            print(change)
        if len(changes_made) > 20:
            print(f"  ... and {len(changes_made) - 20} more changes")
    else:
        print(f"\n✅ No changes needed in {input_file.name}")
        return

    if dry_run:
        print(f"\n🔍 DRY RUN - No files modified")
        return

    # Create backup
    if output_path is None:
        backup_path = input_file.with_suffix('.py.backup')
        shutil.copy2(input_file, backup_path)
        print(f"\n💾 Backup created: {backup_path.name}")
        output_path = input_file

    # Write translated file
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(content)

    print(f"✅ Translation completed: {Path(output_path).name}")
    print(f"   Total replacements: {len(changes_made)}")

def main():
    """Main translation script"""
    print("=" * 60)
    print("🌐 Python GUI Files Translation Script")
    print("=" * 60)
    print("\nTranslating German strings to English...")
    print(f"Translation dictionary: {len(TRANSLATIONS)} entries\n")

    # Files to translate
    files = [
        "Python/start_combine_dbs.py",
        "Python/start_plot_gui.py"
    ]

    # Dry run first
    print("\n🔍 DRY RUN - Preview of changes:\n")
    print("-" * 60)
    for file_path in files:
        translate_file(file_path, dry_run=True)

    # Ask for confirmation
    print("\n" + "=" * 60)
    response = input("\n⚠️  Proceed with translation? (yes/no): ").strip().lower()

    if response == 'yes':
        print("\n🚀 Starting translation...")
        print("-" * 60)
        for file_path in files:
            translate_file(file_path)
        print("\n" + "=" * 60)
        print("✅ Translation completed successfully!")
        print("\n💾 Backup files created with .backup extension")
        print("   If you need to revert, rename .backup files to .py")
    else:
        print("\n❌ Translation cancelled.")

if __name__ == "__main__":
    main()
