# Windows Encryption Setup Guide

## Problem
`pysqlcipher3` hat Kompatibilitätsprobleme auf Windows und lässt sich oft nicht installieren.

## Lösung

### Option 1: Verwenden Sie `sqlcipher3-binary` (EMPFOHLEN für Windows)

Dies ist ein vorkompiliertes Binary-Paket, das auf Windows problemlos funktioniert:

```bash
pip install sqlcipher3-binary
```

### Option 2: Arbeiten Sie nur mit unverschlüsselten Datenbanken

Wenn Sie keine verschlüsselten Datenbanken verwenden möchten, können Sie die Python-Tools ohne SQLCipher nutzen. Die Android-App funktioniert weiterhin mit Verschlüsselung, nur die Python-Tools können verschlüsselte DBs nicht öffnen.

**Setup ohne Verschlüsselung:**
```bash
# In Python/Verzeichnis
python setup.bat
```

Die Python-Tools werden dann nur unverschlüsselte Datenbanken öffnen können.

## Verschlüsselung in der Android-App

Die **Android-App** verwendet weiterhin SQLCipher und funktioniert perfekt mit Verschlüsselung. Dies betrifft nur die Python-Tools!

## Workflow-Optionen

### Workflow A: Mit verschlüsselten Datenbanken (Windows)

1. **Android-App**: Verwenden Sie Verschlüsselung wie gewohnt
2. **Export**: Exportieren Sie die Datenbank aus der App (verschlüsselt)
3. **Python-Tools**: 
   - Installieren Sie `sqlcipher3-binary`: `pip install sqlcipher3-binary`
   - Die Python-Tools können nun verschlüsselte DBs öffnen

### Workflow B: Mit unverschlüsselten Datenbanken

1. **Android-App**: Deaktivieren Sie die Verschlüsselung in den Einstellungen
2. **Export**: Exportieren Sie die Datenbank (unverschlüsselt)
3. **Python-Tools**: Funktionieren ohne zusätzliche Installation

### Workflow C: Temporäre Entschlüsselung (für Python-Tools)

1. **Android-App**: Nutzen Sie Verschlüsselung
2. **Export für Python**: 
   - Deaktivieren Sie temporär die Verschlüsselung
   - Exportieren Sie die Datenbank
   - Aktivieren Sie die Verschlüsselung wieder
3. **Python-Tools**: Verarbeiten Sie die unverschlüsselte DB
4. **Import**: Importieren Sie die DB zurück in die verschlüsselte Android-App

## Getestete Konfigurationen

| OS | Paket | Status |
|---|---|---|
| Windows 10/11 | `sqlcipher3-binary` | ✅ Funktioniert gut |
| Windows 10/11 | `pysqlcipher3` | ❌ Installation schlägt oft fehl |
| Linux | `pysqlcipher3` | ✅ Funktioniert gut |
| macOS | `pysqlcipher3` | ✅ Funktioniert gut |

## Installation von sqlcipher3-binary

### Standard-Installation
```bash
pip install sqlcipher3-binary
```

### In virtueller Umgebung (empfohlen)
```bash
cd Python
python setup.bat
venv\Scripts\activate
pip install sqlcipher3-binary
```

## Überprüfen der Installation

Testen Sie, ob SQLCipher funktioniert:

```bash
cd Python
venv\Scripts\activate
python scripts\database_encryption.py
```

Sie sollten eine der folgenden Meldungen sehen:
- ✅ "Using sqlcipher3-binary for encryption support"
- ✅ "Using pysqlcipher3 for encryption support"
- ⚠️ "WARNING: No SQLCipher library found!" (dann nur unverschlüsselte DBs)

## Fehlerbehebung

### "ERROR: Could not find a version that satisfies the requirement pysqlcipher3"

**Lösung:** Verwenden Sie `sqlcipher3-binary` statt `pysqlcipher3`:
```bash
pip install sqlcipher3-binary
```

### "error: Microsoft Visual C++ 14.0 or greater is required"

**Lösung:** Verwenden Sie das vorkompilierte `sqlcipher3-binary`:
```bash
pip install sqlcipher3-binary
```

### "ImportError: No module named 'sqlcipher3'"

**Lösung:** Installieren Sie das Paket in der virtuellen Umgebung:
```bash
cd Python
venv\Scripts\activate
pip install sqlcipher3-binary
```

## Zusammenfassung

- **Android-App**: Verschlüsselung funktioniert perfekt mit SQLCipher ✅
- **Python-Tools (Windows)**: Verwenden Sie `sqlcipher3-binary` für verschlüsselte DBs
- **Python-Tools (ohne Verschlüsselung)**: Funktionieren ohne zusätzliche Installation
- **Ihre Wahl**: Sie können die Android-App mit Verschlüsselung nutzen und die Python-Tools ohne

Die Skripte erkennen automatisch, ob SQLCipher verfügbar ist und passen sich entsprechend an.
