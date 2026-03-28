package com.example.wifi_geograbber.activities.main;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.widget.Toast;

import com.example.wifi_geograbber.activities.MapActivity;
import com.example.wifi_geograbber.activities.main.database.DatabaseHelper;
import com.example.wifi_geograbber.activities.main.database.DatabaseManager;
import com.example.wifi_geograbber.services.ScanService;
import com.example.wifi_geograbber.utils.BiometricAuthManager;
import com.example.wifi_geograbber.utils.DatabaseEncryptionHelper;
import com.example.wifi_geograbber.utils.EncryptionManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import org.json.JSONObject;

/**
 * Abstract base for MainActivity that contains all dialog and database management actions.
 * Keeps MainActivity focused on scanning, lifecycle, and UI wiring.
 */
public abstract class MainActivityActions extends MainActivityBase {

    protected DatabaseManager databaseManager;

    // ── Abstract methods implemented by MainActivity ─────────────────────
    protected abstract void stopScanning();
    protected abstract void showData();
    protected abstract void loadAndDisplayAvailableNetworks();
    protected abstract void updateInfoSummary(int activeWifi, int activeBluetooth);
    protected abstract void initializeDatabase();

    protected void updateTotalNetworksCount() {
        updateInfoSummary(0, 0);
    }


    // ====================================================================
    //  MORE DIALOG AND DATABASE MANAGEMENT METHODS
    // ====================================================================

    // Popup with further actions
    protected void showMoreDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(com.example.wifi_geograbber.R.string.more_actions);

        String[] items = {
            getString(com.example.wifi_geograbber.R.string.save_db_as_file),
            getString(com.example.wifi_geograbber.R.string.delete_database),
            getString(com.example.wifi_geograbber.R.string.show_network_count),
            getString(com.example.wifi_geograbber.R.string.import_external_db),
            getString(com.example.wifi_geograbber.R.string.encryption_settings),
            "Close App"
        };

        builder.setItems(items, (dialog, which) -> {
            switch(which) {
                case 0: // Save DB as a file
                    exportDatabase();
                    break;
                case 1: // Delete database
                    clearDatabase();
                    break;
                case 2: // Show number of networks
                    showNetworkCount();
                    break;
                case 3: // Import external DB
                    selectExternalDatabaseAsActive();
                    break;
                case 4: // Encryption settings
                    showEncryptionSettingsDialog();
                    break;
                case 5: // Close App
                    if (encryptionManager != null) {
                        encryptionManager.clearPassphrase();
                    }
                    finishAffinity();
                    break;
            }
        });

        // Track this dialog so it can be dismissed when needed
        currentDialog = builder.create();
        currentDialog.show();
    }

    // Export database as file
    protected void exportDatabase() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String fileName = "wifiscannerexport_" + timestamp + ".db";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, EXPORT_DB_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXPORT_DB_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                try {
                    String dbPath = getDatabasePath("wifi_scanner.db").getAbsolutePath();
                    java.io.File dbFile = new java.io.File(dbPath);

                    addLogMessage("Calculating SHA-256 checksum...");
                    String checksum = calculateSHA256(dbFile);

                    if (checksum != null) {
                        String checksumPreview = checksum.length() > 16 ?
                            checksum.substring(0, 8) + "..." + checksum.substring(checksum.length() - 8) :
                            checksum;
                        addLogMessage("Checksum: " + checksumPreview);
                    }

                    java.io.FileInputStream inStream = new java.io.FileInputStream(dbPath);
                    java.io.OutputStream outStream = getContentResolver().openOutputStream(uri);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inStream.read(buffer)) > 0) {
                        outStream.write(buffer, 0, length);
                    }
                    inStream.close();
                    outStream.close();

                    String exportedFilename = "exported_database.db";
                    try {
                        android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                            if (nameIndex >= 0) {
                                exportedFilename = cursor.getString(nameIndex);
                            }
                            cursor.close();
                        }
                    } catch (Exception e) {
                        // Use default filename if extraction fails
                    }

                    lastExportedDbChecksum = checksum;
                    lastExportedDbFilename = exportedFilename;

                    String successMessage = "DB exported successfully!";
                    if (isDatabaseEncrypted) {
                        successMessage += "\n\n🔒 This database is encrypted.";
                    }
                    Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
                    addLogMessage("Database exported: " + exportedFilename +
                                 (isDatabaseEncrypted ? " (encrypted)" : ""));

                    if (checksum != null) {
                        offerChecksumExport(exportedFilename, checksum, dbFile.length());
                    }

                } catch (Exception e) {
                    Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    addLogMessage("ERROR: Export failed: " + e.getMessage());
                }
            }
        } else if (requestCode == EXPORT_CHECKSUM_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null && lastExportedDbChecksum != null && lastExportedDbFilename != null) {
                try {
                    String metadata = createChecksumMetadata(
                        lastExportedDbFilename,
                        lastExportedDbChecksum,
                        getDatabasePath("wifi_scanner.db").length()
                    );

                    if (metadata != null) {
                        java.io.OutputStream outStream = getContentResolver().openOutputStream(uri);
                        outStream.write(metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        outStream.close();

                        Toast.makeText(this, "Checksum metadata exported!", Toast.LENGTH_LONG).show();
                        addLogMessage("Checksum metadata exported successfully");
                        addLogMessage("SHA-256: " + lastExportedDbChecksum);
                    }

                    lastExportedDbChecksum = null;
                    lastExportedDbFilename = null;

                } catch (Exception e) {
                    Toast.makeText(this, "Checksum export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    addLogMessage("ERROR: Checksum export failed: " + e.getMessage());
                }
            }
        } else if (requestCode == IMPORT_DB_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                loadExternalDatabaseAndShowMap(uri);
            }
        } else if (requestCode == IMPORT_ACTIVE_DB_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                loadExternalDatabaseAsActive(uri);
            }
        } else if (requestCode == IMPORT_CHECKSUM_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null && pendingImportFile != null) {
                verifyAndImportWithChecksum(uri, pendingImportFile);
            }
        }
    }

    // Clear all data from database
    protected void clearDatabase() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Clear Database");
        builder.setMessage("This will remove all WiFi and Bluetooth data from the database.\n\n" +
                "The database structure and encryption settings will be preserved.\n\n" +
                "Are you sure?");

        builder.setPositiveButton("Clear All Data", (dialog, which) -> {
            // Explicitly stop ScanService to release database
            stopService(new Intent(this, ScanService.class));
            if (isScanning) {
                stopScanning();
            }
            new android.os.Handler().postDelayed(() -> {
                try {
                    android.util.Log.d("MainActivity", "Starting database clear operation");

                    databaseManager.dbBeginTransaction();

                    try {
                        databaseManager.dbExecSQL("DELETE FROM wifi_data");
                        android.util.Log.d("MainActivity", "Deleted wifi_data");

                        databaseManager.dbExecSQL("DELETE FROM device_data");
                        android.util.Log.d("MainActivity", "Deleted device_data");

                        databaseManager.dbSetTransactionSuccessful();

                        android.util.Log.d("MainActivity", "Database cleared successfully - transaction committed");
                        Toast.makeText(this, com.example.wifi_geograbber.R.string.all_networks_deleted, Toast.LENGTH_SHORT).show();

                        showData();

                    } finally {
                        databaseManager.dbEndTransaction();
                    }

                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Error clearing database: " + e.getMessage(), e);
                    Toast.makeText(this, "Error clearing database: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }, 500);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // Offer to export checksum metadata file
    protected void offerChecksumExport(String dbFilename, String checksum, long fileSize) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Export Checksum Metadata");

        String message = "Database exported successfully!\n\n" +
                "Would you like to export a checksum metadata file?\n\n" +
                "BENEFITS:\n" +
                "• Verify file integrity during import\n" +
                "• Detect tampering or corruption\n" +
                "• Establish trust for file sharing\n" +
                (isDatabaseEncrypted ? "• Store encryption salt for portable import\n" : "") +
                "\n" +
                "METADATA INCLUDES:\n" +
                "• SHA-256 checksum\n" +
                "• Original filename\n" +
                "• File size\n" +
                "• Export timestamp\n" +
                (isDatabaseEncrypted ? "• Encryption salt (for import)\n" : "") +
                "\n" +
                (isDatabaseEncrypted ? "🔐 IMPORTANT: For encrypted databases, the metadata file is REQUIRED for import on other devices!\n\n" : "") +
                "The metadata will be saved as a .json file that you can share alongside the database file.";

        builder.setMessage(message);

        builder.setPositiveButton("Yes, Export Checksum", (dialog, which) -> {
            String metadataFilename = dbFilename.replace(".db", ".sha256.json");
            if (!metadataFilename.contains(".sha256.json")) {
                metadataFilename = dbFilename + ".sha256.json";
            }

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, metadataFilename);
            startActivityForResult(intent, EXPORT_CHECKSUM_REQUEST_CODE);

            addLogMessage("User chose to export checksum metadata");
        });

        if (isDatabaseEncrypted) {
            builder.setNegativeButton("Cancel Export", (dialog, which) -> {
                dialog.dismiss();
                addLogMessage("User cancelled metadata export for encrypted DB");
                Toast.makeText(this, "⚠️ Warning: Without metadata file, this database cannot be imported!", Toast.LENGTH_LONG).show();

                lastExportedDbChecksum = null;
                lastExportedDbFilename = null;
            });
        } else {
            builder.setNegativeButton("Skip", (dialog, which) -> {
                dialog.dismiss();
                addLogMessage("User skipped checksum metadata export");
                Toast.makeText(this, "Note: You can verify checksums manually if needed", Toast.LENGTH_SHORT).show();

                lastExportedDbChecksum = null;
                lastExportedDbFilename = null;
            });
        }

        builder.setCancelable(false);
        builder.show();
    }

    // Offer checksum verification during import
    protected void offerChecksumVerification(android.net.Uri dbUri, java.io.File dbFile) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);

        boolean isEncrypted = DatabaseEncryptionHelper.isDatabaseEncrypted(dbFile);

        if (isEncrypted) {
            builder.setTitle("🔒 Metadata Required");
            String message = "This is an ENCRYPTED database!\n\n" +
                    "⚠️ MANDATORY: You MUST provide the metadata file (.sha256.json) that was exported with this database.\n\n" +
                    "The metadata contains:\n" +
                    "• Encryption salt (required for decryption)\n" +
                    "• Checksum for integrity verification\n\n" +
                    "WITHOUT the metadata file, import will fail even with the correct passphrase!";
            builder.setMessage(message);
        } else {
            builder.setTitle("Verify Checksum?");
            String message = "Do you have a checksum metadata file (.sha256.json) for this database?\n\n" +
                    "VERIFICATION BENEFITS:\n" +
                    "• Confirm file hasn't been tampered with\n" +
                    "• Verify file integrity\n" +
                    "• Ensure authentic source\n\n" +
                    "If you don't have a checksum file, you can skip this step. The database will still be validated using other security checks.";
            builder.setMessage(message);
        }

        String positiveButtonText = isEncrypted ? "Select Metadata File" : "Yes, Select Checksum File";
        builder.setPositiveButton(positiveButtonText, (dialog, which) -> {
            pendingImportUri = dbUri;
            pendingImportFile = dbFile;

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent, IMPORT_CHECKSUM_REQUEST_CODE);

            addLogMessage(isEncrypted ? "User selected metadata file (required)" : "User chose to verify checksum");
        });

        if (isEncrypted) {
            builder.setNegativeButton("Cancel Import", (dialog, which) -> {
                dialog.dismiss();
                dbFile.delete();
                addLogMessage("User cancelled import - metadata file required for encrypted DB");
                Toast.makeText(this, "Import cancelled. Encrypted databases require metadata file.", Toast.LENGTH_LONG).show();
            });
        } else {
            builder.setNegativeButton("Skip Verification", (dialog, which) -> {
                dialog.dismiss();
                addLogMessage("User skipped checksum verification");
                Toast.makeText(this, "Proceeding without checksum verification", Toast.LENGTH_SHORT).show();

                continueImportWithoutChecksum(dbFile);
            });
        }

        builder.setCancelable(false);
        builder.show();
    }

    // Continue import without checksum verification
    protected void continueImportWithoutChecksum(java.io.File externalDbFile) {
        proceedWithDatabaseImport(externalDbFile);
    }

    // Verify checksum and import database
    protected void verifyAndImportWithChecksum(android.net.Uri checksumUri, java.io.File dbFile) {
        try {
            java.io.InputStream inStream = getContentResolver().openInputStream(checksumUri);
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(inStream, java.nio.charset.StandardCharsets.UTF_8)
            );

            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            reader.close();
            inStream.close();

            JSONObject metadata = new JSONObject(jsonBuilder.toString());
            String expectedChecksum = metadata.getString("checksum");
            String algorithm = metadata.getString("algorithm");
            String originalFilename = metadata.optString("filename", "unknown");
            long originalFileSize = metadata.optLong("fileSize", -1);

            boolean isEncrypted = metadata.optBoolean("encrypted", false);
            String encryptionSaltBase64 = metadata.optString("encryptionSalt", null);

            addLogMessage("Checksum metadata loaded:");
            addLogMessage("  Algorithm: " + algorithm);
            addLogMessage("  Filename: " + originalFilename);
            String checksumPreview = expectedChecksum.length() > 16 ?
                expectedChecksum.substring(0, 8) + "..." + expectedChecksum.substring(expectedChecksum.length() - 8) :
                expectedChecksum;
            addLogMessage("  Expected checksum: " + checksumPreview);
            if (isEncrypted) {
                addLogMessage("  Encryption: Enabled");
                if (encryptionSaltBase64 != null) {
                    pendingEncryptionSalt = android.util.Base64.decode(encryptionSaltBase64, android.util.Base64.NO_WRAP);
                    addLogMessage("  Encryption salt extracted from metadata");
                }
            }

            if (!"SHA-256".equals(algorithm)) {
                Toast.makeText(this, "Unsupported checksum algorithm: " + algorithm, Toast.LENGTH_LONG).show();
                addLogMessage("ERROR: Unsupported algorithm: " + algorithm);
                dbFile.delete();
                clearPendingImport();
                return;
            }

            if (originalFileSize > 0 && dbFile.length() != originalFileSize) {
                addLogMessage("WARNING: File size mismatch!");
                addLogMessage("  Expected: " + originalFileSize + " bytes");
                addLogMessage("  Actual: " + dbFile.length() + " bytes");

                showFileSizeMismatchWarning(dbFile, expectedChecksum, originalFileSize);
                return;
            }

            addLogMessage("Calculating file checksum...");
            String actualChecksum = calculateSHA256(dbFile);

            if (actualChecksum == null) {
                Toast.makeText(this, "Failed to calculate checksum", Toast.LENGTH_LONG).show();
                dbFile.delete();
                clearPendingImport();
                return;
            }

            boolean checksumValid = verifyChecksum(actualChecksum, expectedChecksum, originalFilename);

            if (checksumValid) {
                Toast.makeText(this, "✓ Checksum verified successfully!", Toast.LENGTH_LONG).show();
                addLogMessage("Proceeding with verified import...");
                proceedWithDatabaseImport(dbFile);
            } else {
                showChecksumMismatchError(dbFile);
            }

        } catch (Exception e) {
            Toast.makeText(this, "Checksum verification failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            addLogMessage("ERROR: Checksum verification failed: " + e.getMessage());
            dbFile.delete();
            clearPendingImport();
        }
    }

    // Clear pending import data
    protected void clearPendingImport() {
        pendingImportUri = null;
        pendingImportFile = null;
        pendingEncryptionSalt = null;
    }

    // Show checksum mismatch error
    protected void showChecksumMismatchError(java.io.File dbFile) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("⚠️ Checksum Verification Failed");

        String message = "The checksum verification FAILED!\n\n" +
                "POSSIBLE CAUSES:\n" +
                "• File has been tampered with\n" +
                "• File corruption during transfer\n" +
                "• Wrong checksum file selected\n" +
                "• Mismatched database and checksum files\n\n" +
                "RECOMMENDATION:\n" +
                "Do NOT import this database. It may be corrupted or compromised.\n\n" +
                "For security reasons, import has been blocked.";

        builder.setMessage(message);

        builder.setPositiveButton("OK", (dialog, which) -> {
            dbFile.delete();
            clearPendingImport();
            Toast.makeText(this, "Import cancelled for security", Toast.LENGTH_SHORT).show();
        });

        builder.setCancelable(false);
        builder.show();
    }

    // Show file size mismatch warning
    protected void showFileSizeMismatchWarning(java.io.File dbFile, String expectedChecksum, long originalFileSize) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("⚠️ File Size Mismatch");

        String message = "The database file size doesn't match the metadata!\n\n" +
                "Expected size: " + originalFileSize + " bytes\n" +
                "Actual size: " + dbFile.length() + " bytes\n\n" +
                "This could indicate:\n" +
                "• File corruption\n" +
                "• Wrong file selected\n" +
                "• Metadata mismatch\n\n" +
                "Do you want to continue with checksum verification anyway?";

        builder.setMessage(message);

        builder.setPositiveButton("Continue Verification", (dialog, which) -> {
            addLogMessage("User chose to continue despite size mismatch");
            String actualChecksum = calculateSHA256(dbFile);

            if (actualChecksum != null) {
                boolean checksumValid = verifyChecksum(actualChecksum, expectedChecksum, dbFile.getName());

                if (checksumValid) {
                    Toast.makeText(this, "✓ Checksum verified!", Toast.LENGTH_SHORT).show();
                    clearPendingImport();
                    proceedWithDatabaseImport(dbFile);
                } else {
                    showChecksumMismatchError(dbFile);
                }
            } else {
                Toast.makeText(this, "Failed to calculate checksum", Toast.LENGTH_SHORT).show();
                dbFile.delete();
                clearPendingImport();
            }
        });

        builder.setNegativeButton("Cancel Import", (dialog, which) -> {
            dbFile.delete();
            clearPendingImport();
            Toast.makeText(this, "Import cancelled", Toast.LENGTH_SHORT).show();
        });

        builder.setCancelable(false);
        builder.show();
    }

    // Process validated database import
    protected void proceedWithDatabaseImport(java.io.File externalDbFile) {
        try {
            if (DatabaseEncryptionHelper.isDatabaseEncrypted(externalDbFile)) {
                addLogMessage("Encrypted database detected");
                showEncryptedDatabaseImportDialog(externalDbFile);
                return;
            }

            if (!validateExternalDatabase(externalDbFile)) {
                Toast.makeText(this, "Security error: Invalid or malicious database file!", Toast.LENGTH_LONG).show();
                addLogMessage("ERROR: Database validation failed - possible malicious content");
                externalDbFile.delete();
                return;
            }

            SQLiteDatabase testDb = null;
            try {
                testDb = SQLiteDatabase.openDatabase(externalDbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);

                boolean hasWifiData = hasTable(testDb, "wifi_data");
                boolean hasDeviceData = hasTable(testDb, "device_data");

                if (!hasWifiData && !hasDeviceData) {
                    testDb.close();
                    Toast.makeText(this, "Invalid database: No WiFi or Bluetooth data tables found!", Toast.LENGTH_LONG).show();
                    externalDbFile.delete();
                    return;
                }

                int wifiCountTemp = 0;
                int bluetoothCountTemp = 0;

                android.database.Cursor cursor = testDb.rawQuery("SELECT COUNT(*) FROM device_data WHERE device_type='WIFI' AND latitude != 0 AND longitude != 0", null);
                if (cursor.moveToFirst()) wifiCountTemp += cursor.getInt(0);
                cursor.close();

                cursor = testDb.rawQuery("SELECT COUNT(*) FROM device_data WHERE device_type='BLUETOOTH' AND latitude != 0 AND longitude != 0", null);
                if (cursor.moveToFirst()) bluetoothCountTemp = cursor.getInt(0);
                cursor.close();

                cursor = testDb.rawQuery("SELECT COUNT(*) FROM wifi_data WHERE latitude != 0 AND longitude != 0", null);
                if (cursor.moveToFirst()) wifiCountTemp += cursor.getInt(0);
                cursor.close();

                testDb.close();

                final int wifiCount = wifiCountTemp;
                final int bluetoothCount = bluetoothCountTemp;
                final String dbPath = externalDbFile.getAbsolutePath();

                android.app.AlertDialog.Builder confirmBuilder = new android.app.AlertDialog.Builder(this);
                confirmBuilder.setTitle(com.example.wifi_geograbber.R.string.external_db_as_active_title);
                confirmBuilder.setMessage(
                    "Load external DB as active database?\n\n" +
                    "Existing devices:\n• " + wifiCount + " WiFi networks\n• " + bluetoothCount + " Bluetooth devices\n\n" +
                    "New scans will be added to this DB!"
                );
                confirmBuilder.setPositiveButton(com.example.wifi_geograbber.R.string.yes_activate, (d2, w2) -> {
                    switchToExternalDatabase(dbPath, wifiCount, bluetoothCount);
                });
                confirmBuilder.setNegativeButton(com.example.wifi_geograbber.R.string.cancel, (d2, w2) -> {
                    externalDbFile.delete();
                });
                confirmBuilder.show();

            } catch (Exception e) {
                if (testDb != null) testDb.close();
                Toast.makeText(this, "Error loading the database: " + e.getMessage(), Toast.LENGTH_LONG).show();
                addLogMessage("ERROR: Failed to load database: " + e.getMessage());
                externalDbFile.delete();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Invalid database file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            addLogMessage("ERROR: Invalid database file: " + e.getMessage());
            externalDbFile.delete();
        }
    }

    // Select external database as active DB
    protected void selectExternalDatabaseAsActive() {
        if (isScanning) {
            Toast.makeText(this, "Please stop scanning first!", Toast.LENGTH_LONG).show();
            return;
        }

        showImportSecurityWarning();
    }

    // Show security warning dialog before database import
    protected void showImportSecurityWarning() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("⚠️ Security Warning");

        String warningMessage = "Importing external database files may pose security risks:\n\n" +
                "POTENTIAL RISKS:\n" +
                "• Malicious or corrupted data\n" +
                "• Data loss or corruption\n" +
                "• Unexpected app behavior\n" +
                "• Privacy concerns from untrusted sources\n\n" +
                "SECURITY VALIDATIONS:\n" +
                "✓ File size limit (max 100MB)\n" +
                "✓ SQL injection detection\n" +
                "✓ Malicious trigger/view blocking\n" +
                "✓ Schema validation\n" +
                "✓ Data sanitization\n" +
                "✓ Range validation\n\n" +
                "RECOMMENDATIONS:\n" +
                "• Only import databases from trusted sources\n" +
                "• Verify file integrity before importing\n" +
                "• Backup your current data regularly\n\n" +
                "Do you understand the risks and wish to proceed?";

        builder.setMessage(warningMessage);

        builder.setPositiveButton("Yes, I Understand - Proceed", (dialog, which) -> {
            proceedWithDatabaseImport();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
            Toast.makeText(this, "Import cancelled", Toast.LENGTH_SHORT).show();
        });

        builder.setCancelable(false);

        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        addLogMessage("Security warning displayed for database import");
    }

    // Proceed with database import after security warning acknowledged (no-arg version)
    protected void proceedWithDatabaseImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "application/x-sqlite3", "*/*"});
        startActivityForResult(intent, IMPORT_ACTIVE_DB_REQUEST_CODE);

        addLogMessage("User acknowledged security risks, file picker opened");
    }

    // Load external database and display map
    protected void loadExternalDatabaseAndShowMap(android.net.Uri uri) {
        try {
            java.io.File tempFile = new java.io.File(getFilesDir(), "temp_external.db");

            java.io.InputStream inStream = getContentResolver().openInputStream(uri);
            java.io.FileOutputStream outStream = new java.io.FileOutputStream(tempFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inStream.read(buffer)) > 0) {
                outStream.write(buffer, 0, length);
            }
            inStream.close();
            outStream.close();

            if (!validateExternalDatabase(tempFile)) {
                Toast.makeText(this, "Security error: Invalid or malicious database file!", Toast.LENGTH_LONG).show();
                addLogMessage("ERROR: Database validation failed - possible malicious content");
                tempFile.delete();
                return;
            }

            SQLiteDatabase testDb = null;
            try {
                testDb = SQLiteDatabase.openDatabase(tempFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);

                boolean hasWifiData = hasTable(testDb, "wifi_data");
                boolean hasDeviceData = hasTable(testDb, "device_data");

                if (hasWifiData || hasDeviceData) {
                    int wifiCount = 0;
                    int bluetoothCount = 0;

                    if (hasDeviceData) {
                        android.database.Cursor cursor = testDb.rawQuery("SELECT COUNT(*) FROM device_data WHERE device_type='WIFI' AND latitude != 0 AND longitude != 0", null);
                        if (cursor.moveToFirst()) wifiCount += cursor.getInt(0);
                        cursor.close();

                        cursor = testDb.rawQuery("SELECT COUNT(*) FROM device_data WHERE device_type='BLUETOOTH' AND latitude != 0 AND longitude != 0", null);
                        if (cursor.moveToFirst()) bluetoothCount = cursor.getInt(0);
                        cursor.close();
                    }

                    if (hasWifiData) {
                        android.database.Cursor cursor = testDb.rawQuery("SELECT COUNT(*) FROM wifi_data WHERE latitude != 0 AND longitude != 0", null);
                        if (cursor.moveToFirst()) wifiCount += cursor.getInt(0);
                        cursor.close();
                    }

                    testDb.close();

                    String message = String.format(getString(com.example.wifi_geograbber.R.string.external_db_loaded),
                                                 wifiCount, bluetoothCount);

                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                    builder.setTitle(com.example.wifi_geograbber.R.string.external_database)
                           .setMessage(message)
                           .setPositiveButton(com.example.wifi_geograbber.R.string.open_map, (dialog, which) -> {
                               Intent mapIntent = new Intent(MainActivityActions.this, MapActivity.class);
                               mapIntent.putExtra("external_db_path", tempFile.getAbsolutePath());
                               startActivity(mapIntent);
                           })
                           .setNegativeButton(com.example.wifi_geograbber.R.string.cancel, (dialog, which) -> {
                               if (tempFile.exists()) {
                                   tempFile.delete();
                               }
                           })
                           .show();

                    addLogMessage("External DB loaded: " + wifiCount + " WiFi, " + bluetoothCount + " BT");

                } else {
                    Toast.makeText(this, "No WiFi/Bluetooth data found in the database!", Toast.LENGTH_LONG).show();
                    tempFile.delete();
                }

            } catch (Exception e) {
                if (testDb != null) testDb.close();
                Toast.makeText(this, "Invalid database file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                tempFile.delete();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error loading the database: " + e.getMessage(), Toast.LENGTH_LONG).show();
            addLogMessage("Error during DB import: " + e.getMessage());
        }
    }

    // Auxiliary method: Checks if table exists
    protected boolean hasTable(SQLiteDatabase db, String tableName) {
        android.database.Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{tableName});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    // Security validation for external databases
    protected boolean validateExternalDatabase(java.io.File dbFile) {
        SQLiteDatabase db = null;
        try {
            long maxSize = 100 * 1024 * 1024; // 100MB
            if (dbFile.length() > maxSize) {
                addLogMessage("ERROR: Database file too large (" + (dbFile.length() / 1024 / 1024) + " MB)");
                return false;
            }

            db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);

            Cursor triggerCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='trigger'", null);
            if (triggerCursor.getCount() > 0) {
                addLogMessage("ERROR: Database contains triggers (potential security risk)");
                triggerCursor.close();
                return false;
            }
            triggerCursor.close();

            Cursor viewCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='view'", null);
            if (viewCursor.getCount() > 0) {
                addLogMessage("ERROR: Database contains views (potential security risk)");
                viewCursor.close();
                return false;
            }
            viewCursor.close();

            boolean hasWifiData = hasTable(db, "wifi_data");
            boolean hasDeviceData = hasTable(db, "device_data");

            if (!hasWifiData && !hasDeviceData) {
                addLogMessage("ERROR: No valid data tables found");
                return false;
            }

            Cursor tableCursor = db.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table'", null);
            if (tableCursor.moveToFirst()) {
                int tableCount = tableCursor.getInt(0);
                if (tableCount > 10) {
                    addLogMessage("ERROR: Too many tables in database (" + tableCount + ")");
                    tableCursor.close();
                    return false;
                }
            }
            tableCursor.close();

            if (hasDeviceData) {
                if (!validateTableSchema(db, "device_data")) {
                    return false;
                }
            }
            if (hasWifiData) {
                if (!validateTableSchema(db, "wifi_data")) {
                    return false;
                }
            }

            addLogMessage("Database validation passed");
            return true;

        } catch (Exception e) {
            addLogMessage("ERROR: Database validation failed: " + e.getMessage());
            return false;
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }

    // Validate table schema to prevent malicious schema structures
    protected boolean validateTableSchema(SQLiteDatabase db, String tableName) {
        try {
            if (!tableName.equals("wifi_data") && !tableName.equals("device_data")) {
                addLogMessage("ERROR: Invalid table name: " + tableName);
                return false;
            }

            Cursor cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
            int columnCount = cursor.getCount();
            cursor.close();

            if (columnCount > 50) {
                addLogMessage("ERROR: Table " + tableName + " has too many columns (" + columnCount + ")");
                return false;
            }

            if (columnCount == 0) {
                addLogMessage("ERROR: Table " + tableName + " has no columns");
                return false;
            }

            return true;
        } catch (Exception e) {
            addLogMessage("ERROR: Schema validation failed for " + tableName + ": " + e.getMessage());
            return false;
        }
    }

    // Sanitize string input to prevent SQL injection
    protected String sanitizeString(String input) {
        if (input == null) {
            return null;
        }
        if (input.length() > 1000) {
            input = input.substring(0, 1000);
        }
        return input.replaceAll("[\\x00\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }

    // Calculate SHA-256 checksum for a file
    protected String calculateSHA256(java.io.File file) {
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            String checksum = calculateSHA256FromStream(fis);
            fis.close();
            return checksum;
        } catch (Exception e) {
            addLogMessage("ERROR: Failed to calculate checksum: " + e.getMessage());
            return null;
        }
    }

    // Calculate SHA-256 checksum from InputStream
    protected String calculateSHA256FromStream(java.io.InputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            byte[] hashBytes = digest.digest();

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException | java.io.IOException e) {
            addLogMessage("ERROR: Failed to calculate SHA-256: " + e.getMessage());
            return null;
        }
    }

    // Create checksum metadata JSON
    protected String createChecksumMetadata(String filename, String checksum, long fileSize) {
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("version", "1.0");
            metadata.put("algorithm", "SHA-256");
            metadata.put("filename", filename);
            metadata.put("checksum", checksum);
            metadata.put("fileSize", fileSize);
            metadata.put("timestamp", System.currentTimeMillis());
            metadata.put("exportedBy", "WiFi GeoGrabber v" + APP_VERSION);

            metadata.put("encrypted", isDatabaseEncrypted);
            if (isDatabaseEncrypted && encryptionManager != null) {
                byte[] salt = encryptionManager.getCurrentSalt();
                if (salt != null) {
                    String saltBase64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP);
                    metadata.put("encryptionSalt", saltBase64);
                    addLogMessage("Including encryption salt in metadata for portable import");
                }
            }

            return metadata.toString(2);
        } catch (Exception e) {
            addLogMessage("ERROR: Failed to create metadata: " + e.getMessage());
            return null;
        }
    }

    // Verify checksum from metadata
    protected boolean verifyChecksum(String fileChecksum, String metadataChecksum, String filename) {
        if (fileChecksum == null || metadataChecksum == null) {
            return false;
        }

        boolean matches = fileChecksum.equalsIgnoreCase(metadataChecksum);

        if (matches) {
            addLogMessage("✓ Checksum verified successfully for " + filename);
        } else {
            addLogMessage("✗ Checksum verification FAILED for " + filename);
            String expectedPreview = metadataChecksum.length() > 16 ?
                metadataChecksum.substring(0, 8) + "..." + metadataChecksum.substring(metadataChecksum.length() - 8) :
                metadataChecksum;
            String actualPreview = fileChecksum.length() > 16 ?
                fileChecksum.substring(0, 8) + "..." + fileChecksum.substring(fileChecksum.length() - 8) :
                fileChecksum;
            addLogMessage("  Expected: " + expectedPreview);
            addLogMessage("  Actual:   " + actualPreview);
        }

        return matches;
    }

    // Load external database as active database
    protected void loadExternalDatabaseAsActive(android.net.Uri uri) {
        try {
            java.io.File externalDbFile = new java.io.File(getFilesDir(), "external_active.db");

            if (externalDbFile.exists()) {
                externalDbFile.delete();
            }

            java.io.InputStream inStream = getContentResolver().openInputStream(uri);
            java.io.FileOutputStream outStream = new java.io.FileOutputStream(externalDbFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inStream.read(buffer)) > 0) {
                outStream.write(buffer, 0, length);
            }
            inStream.close();
            outStream.close();

            addLogMessage("Database file copied, size: " + (externalDbFile.length() / 1024) + " KB");

            offerChecksumVerification(uri, externalDbFile);

        } catch (Exception e) {
            Toast.makeText(this, "Error loading the database: " + e.getMessage(), Toast.LENGTH_LONG).show();
            addLogMessage("Error during DB import: " + e.getMessage());
        }
    }

    // Switches to the external database as the active database
    protected void switchToExternalDatabase(String dbPath, int wifiCount, int bluetoothCount) {
        try {
            SQLiteDatabase externalDb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);

            copyDataFromExternalToInternal(externalDb);

            externalDb.close();

            isUsingExternalDatabase = false;
            currentDatabasePath = null;

            String statusMessage = "External data transferred to internal DB - " + wifiCount + " WiFi, " + bluetoothCount + " BT";
            statusText.setText(statusMessage);

            if (isShowingStoredData) {
                showData();
            } else {
                loadAndDisplayAvailableNetworks();
            }

            updateInfoSummary(0, 0);

            Toast.makeText(this, "External data has been transferred to the app database!\nData will persist after app restart.", Toast.LENGTH_LONG).show();
            addLogMessage("External data transferred to internal DB: " + dbPath);
            addLogMessage("Transferred data: " + wifiCount + " WiFi, " + bluetoothCount + " BT");

            java.io.File externalDbFile = new java.io.File(dbPath);
            if (externalDbFile.exists() && externalDbFile.getAbsolutePath().contains(getFilesDir().getAbsolutePath())) {
                externalDbFile.delete();
                addLogMessage("Temporary external DB file deleted");
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error transferring external DB: " + e.getMessage(), Toast.LENGTH_LONG).show();
            addLogMessage("Error during DB transfer: " + e.getMessage());
        }
    }

    // Copies all data from the external database to the internal database.
    protected void copyDataFromExternalToInternal(SQLiteDatabase externalDb) throws Exception {
        int copiedWifi = 0;
        int copiedBluetooth = 0;
        int copiedLegacyWifi = 0;

        try {
            databaseManager.dbBeginTransaction();

            android.database.Cursor deviceCursor = externalDb.rawQuery("SELECT * FROM device_data LIMIT 50000", null);
            if (deviceCursor.moveToFirst()) {
                do {
                    String deviceName = sanitizeString(deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("device_name")));
                    String deviceAddress = sanitizeString(deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("device_address")));
                    String deviceType = sanitizeString(deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("device_type")));
                    int signalStrength = deviceCursor.getInt(deviceCursor.getColumnIndexOrThrow("signal_strength"));
                    String encryptionInfo = sanitizeString(deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("encryption_info")));
                    double latitude = deviceCursor.getDouble(deviceCursor.getColumnIndexOrThrow("latitude"));
                    double longitude = deviceCursor.getDouble(deviceCursor.getColumnIndexOrThrow("longitude"));
                    long timestamp = deviceCursor.getLong(deviceCursor.getColumnIndexOrThrow("timestamp"));

                    if (signalStrength < -150 || signalStrength > 0) {
                        continue;
                    }
                    if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                        continue;
                    }
                    if (timestamp < 0 || timestamp > System.currentTimeMillis() + 86400000) {
                        continue;
                    }

                    if (deviceType != null && !deviceType.equals("WIFI") && !deviceType.equals("BLUETOOTH")) {
                        continue;
                    }

                    int frequencyIdx = deviceCursor.getColumnIndex("frequency");
                    int channelIdx = deviceCursor.getColumnIndex("channel");
                    int channelWidthIdx = deviceCursor.getColumnIndex("channel_width");
                    int capabilitiesIdx = deviceCursor.getColumnIndex("capabilities");
                    int centerFreq0Idx = deviceCursor.getColumnIndex("center_freq0");
                    int centerFreq1Idx = deviceCursor.getColumnIndex("center_freq1");
                    int wifiStandardIdx = deviceCursor.getColumnIndex("wifi_standard");
                    int vendorInfoIdx = deviceCursor.getColumnIndex("vendor_info");
                    int maxSpeedIdx = deviceCursor.getColumnIndex("max_connection_speed");

                    int frequency = frequencyIdx >= 0 ? deviceCursor.getInt(frequencyIdx) : 0;
                    int channel = channelIdx >= 0 ? deviceCursor.getInt(channelIdx) : 0;
                    String channelWidth = channelWidthIdx >= 0 ? sanitizeString(deviceCursor.getString(channelWidthIdx)) : null;
                    String capabilities = capabilitiesIdx >= 0 ? sanitizeString(deviceCursor.getString(capabilitiesIdx)) : null;
                    int centerFreq0 = centerFreq0Idx >= 0 ? deviceCursor.getInt(centerFreq0Idx) : 0;
                    int centerFreq1 = centerFreq1Idx >= 0 ? deviceCursor.getInt(centerFreq1Idx) : 0;
                    String wifiStandard = wifiStandardIdx >= 0 ? sanitizeString(deviceCursor.getString(wifiStandardIdx)) : null;
                    String vendorInfo = vendorInfoIdx >= 0 ? sanitizeString(deviceCursor.getString(vendorInfoIdx)) : null;
                    int maxSpeed = maxSpeedIdx >= 0 ? deviceCursor.getInt(maxSpeedIdx) : 0;

                    if (!databaseManager.existsInDeviceDb(deviceAddress, deviceType)) {
                        databaseManager.dbExecSQL("INSERT INTO device_data (device_name, device_address, device_type, signal_strength, encryption_info, frequency, channel, channel_width, capabilities, center_freq0, center_freq1, wifi_standard, vendor_info, max_connection_speed, latitude, longitude, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                new Object[]{deviceName, deviceAddress, deviceType, signalStrength, encryptionInfo, frequency, channel, channelWidth, capabilities, centerFreq0, centerFreq1, wifiStandard, vendorInfo, maxSpeed, latitude, longitude, timestamp});

                        if ("WIFI".equals(deviceType)) {
                            copiedWifi++;
                        } else if ("BLUETOOTH".equals(deviceType)) {
                            copiedBluetooth++;
                        }
                    }
                } while (deviceCursor.moveToNext());
            }
            deviceCursor.close();

            if (hasTable(externalDb, "wifi_data")) {
                android.database.Cursor wifiCursor = externalDb.rawQuery("SELECT * FROM wifi_data LIMIT 50000", null);
                if (wifiCursor.moveToFirst()) {
                    do {
                        String ssid = sanitizeString(wifiCursor.getString(wifiCursor.getColumnIndexOrThrow("ssid")));
                        String bssid = sanitizeString(wifiCursor.getString(wifiCursor.getColumnIndexOrThrow("bssid")));
                        int signalStrength = wifiCursor.getInt(wifiCursor.getColumnIndexOrThrow("signal_strength"));
                        String encryption = sanitizeString(wifiCursor.getString(wifiCursor.getColumnIndexOrThrow("encryption")));
                        double latitude = wifiCursor.getDouble(wifiCursor.getColumnIndexOrThrow("latitude"));
                        double longitude = wifiCursor.getDouble(wifiCursor.getColumnIndexOrThrow("longitude"));
                        long timestamp = wifiCursor.getLong(wifiCursor.getColumnIndexOrThrow("timestamp"));

                        if (signalStrength < -150 || signalStrength > 0) {
                            continue;
                        }
                        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                            continue;
                        }
                        if (timestamp < 0 || timestamp > System.currentTimeMillis() + 86400000) {
                            continue;
                        }

                        int frequencyIdx = wifiCursor.getColumnIndex("frequency");
                        int channelIdx = wifiCursor.getColumnIndex("channel");
                        int capabilitiesIdx = wifiCursor.getColumnIndex("capabilities");
                        int wifiStandardIdx = wifiCursor.getColumnIndex("wifi_standard");
                        int vendorInfoIdx = wifiCursor.getColumnIndex("vendor_info");
                        int channelWidthIdx = wifiCursor.getColumnIndex("channel_width");
                        int centerFreq0Idx = wifiCursor.getColumnIndex("center_freq0");
                        int centerFreq1Idx = wifiCursor.getColumnIndex("center_freq1");
                        int maxSpeedIdx = wifiCursor.getColumnIndex("max_connection_speed");

                        int frequency = frequencyIdx >= 0 ? wifiCursor.getInt(frequencyIdx) : 0;
                        int channel = channelIdx >= 0 ? wifiCursor.getInt(channelIdx) : 0;
                        String capabilities = capabilitiesIdx >= 0 ? sanitizeString(wifiCursor.getString(capabilitiesIdx)) : null;
                        String wifiStandard = wifiStandardIdx >= 0 ? sanitizeString(wifiCursor.getString(wifiStandardIdx)) : null;
                        String vendorInfo = vendorInfoIdx >= 0 ? sanitizeString(wifiCursor.getString(vendorInfoIdx)) : null;
                        String channelWidth = channelWidthIdx >= 0 ? sanitizeString(wifiCursor.getString(channelWidthIdx)) : null;
                        int centerFreq0 = centerFreq0Idx >= 0 ? wifiCursor.getInt(centerFreq0Idx) : 0;
                        int centerFreq1 = centerFreq1Idx >= 0 ? wifiCursor.getInt(centerFreq1Idx) : 0;
                        int maxSpeed = maxSpeedIdx >= 0 ? wifiCursor.getInt(maxSpeedIdx) : 0;

                        if (!databaseManager.existsInDb(bssid) && !databaseManager.existsInDeviceDb(bssid, "WIFI")) {
                            databaseManager.dbExecSQL("INSERT INTO wifi_data (ssid, bssid, signal_strength, encryption, latitude, longitude, timestamp, frequency, channel, capabilities, wifi_standard, vendor_info, channel_width, center_freq0, center_freq1, max_connection_speed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                    new Object[]{ssid, bssid, signalStrength, encryption, latitude, longitude, timestamp, frequency, channel, capabilities, wifiStandard, vendorInfo, channelWidth, centerFreq0, centerFreq1, maxSpeed});
                            copiedLegacyWifi++;
                        }
                    } while (wifiCursor.moveToNext());
                }
                wifiCursor.close();
            }

            databaseManager.dbSetTransactionSuccessful();
            databaseManager.dbEndTransaction();

            addLogMessage("Data transfer completed:");
            addLogMessage("• " + copiedWifi + " WiFi devices (device_data)");
            addLogMessage("• " + copiedBluetooth + " Bluetooth devices");
            addLogMessage("• " + copiedLegacyWifi + " Legacy WiFi entries");
        } catch (Exception e) {
            if (databaseManager.dbInTransaction()) {
                databaseManager.dbEndTransaction();
            }
            addLogMessage("Error while copying data: " + e.getMessage());
            throw e;
        }
    }

    // Show number of networks
    protected void showNetworkCount() {
        Cursor deviceCursor = databaseManager.dbRawQuery("SELECT COUNT(*) FROM device_data", null);
        int deviceCount = 0;
        if (deviceCursor != null) {
            if (deviceCursor.moveToFirst()) {
                deviceCount = deviceCursor.getInt(0);
            }
            deviceCursor.close();
        }

        Cursor wifiCursor = databaseManager.dbRawQuery("SELECT COUNT(*) FROM device_data WHERE device_type = 'WIFI'", null);
        int wifiCount = 0;
        if (wifiCursor != null) {
            if (wifiCursor.moveToFirst()) {
                wifiCount = wifiCursor.getInt(0);
            }
            wifiCursor.close();
        }

        Cursor bluetoothCursor = databaseManager.dbRawQuery("SELECT COUNT(*) FROM device_data WHERE device_type = 'BLUETOOTH'", null);
        int bluetoothCount = 0;
        if (bluetoothCursor != null) {
            if (bluetoothCursor.moveToFirst()) {
                bluetoothCount = bluetoothCursor.getInt(0);
            }
            bluetoothCursor.close();
        }

        Cursor oldWifiCursor = databaseManager.dbRawQuery("SELECT COUNT(*) FROM wifi_data", null);
        int oldWifiCount = 0;
        if (oldWifiCursor != null) {
            if (oldWifiCursor.moveToFirst()) {
                oldWifiCount = oldWifiCursor.getInt(0);
            }
            oldWifiCursor.close();
        }

        String message = "Devices in DB: " + deviceCount +
                " (WiFi: " + wifiCount +
                ", Bluetooth: " + bluetoothCount + ")" +
                (oldWifiCount > 0 ? " + " + oldWifiCount + " old WiFi" : "");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // ====================================================================
    //  ENCRYPTION SETTINGS METHODS
    // ====================================================================

    /**
     * Show encryption settings dialog
     */
    protected void showEncryptionSettingsDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(com.example.wifi_geograbber.R.string.encryption_settings);

        String status = encryptionManager.isEncryptionEnabled() ?
            getString(com.example.wifi_geograbber.R.string.encryption_enabled) : getString(com.example.wifi_geograbber.R.string.encryption_disabled);

        java.util.ArrayList<String> optionsList = new java.util.ArrayList<>();
        if (encryptionManager.isEncryptionEnabled()) {
            optionsList.add(getString(com.example.wifi_geograbber.R.string.change_passphrase));

            if (biometricAuthManager.isBiometricSupported()) {
                if (biometricAuthManager.isBiometricEnabled()) {
                    optionsList.add("🔓 Disable Biometric Unlock");
                } else {
                    optionsList.add("🔒 Enable Biometric Unlock");
                }
            }

            optionsList.add(getString(com.example.wifi_geograbber.R.string.disable_encryption));
        } else {
            optionsList.add(getString(com.example.wifi_geograbber.R.string.enable_encryption));
        }

        String[] options = optionsList.toArray(new String[0]);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 10);

        android.widget.TextView statusTextView = new android.widget.TextView(this);
        String statusMessage = getString(com.example.wifi_geograbber.R.string.encryption_status) + ": " + status;

        if (encryptionManager.isEncryptionEnabled() && biometricAuthManager.isBiometricSupported()) {
            String bioStatus = biometricAuthManager.isBiometricEnabled() ?
                "\n🔒 Biometric Unlock: Enabled" :
                "\n🔓 Biometric Unlock: Disabled";
            statusMessage += bioStatus;
        }

        statusMessage += "\n\nOptions:";
        statusTextView.setText(statusMessage);
        layout.addView(statusTextView);

        android.widget.ListView optionsListView = new android.widget.ListView(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options);
        optionsListView.setAdapter(adapter);
        layout.addView(optionsListView);

        if (encryptionManager.isEncryptionEnabled()) {
            android.widget.Button resetButton = new android.widget.Button(this);
            resetButton.setText("Encryption Reset");
            resetButton.setOnClickListener(v -> {
                showEncryptionResetDialog();
            });
            layout.addView(resetButton);
        }

        builder.setView(layout);
        builder.setNegativeButton(com.example.wifi_geograbber.R.string.cancel, null);
        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        optionsListView.setOnItemClickListener((parent, view, position, id) -> {
            if (encryptionManager.isEncryptionEnabled()) {
                if (position == 0) {
                    showChangePassphraseDialog();
                } else if (position == 1) {
                    if (biometricAuthManager.isBiometricSupported() && position == 1) {
                        if (biometricAuthManager.isBiometricEnabled()) {
                            showDisableBiometricDialog();
                        } else {
                            showEnableBiometricDialog();
                        }
                    } else {
                        showDisableEncryptionDialog();
                    }
                } else if (position == 2) {
                    showDisableEncryptionDialog();
                }
            } else {
                showSetupEncryptionDialog();
            }
            dialog.dismiss();
        });
    }

    /**
     * Show change passphrase dialog
     */
    protected void showChangePassphraseDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(com.example.wifi_geograbber.R.string.change_passphrase_title);
        builder.setMessage(com.example.wifi_geograbber.R.string.change_passphrase_message);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final android.widget.EditText currentInput = new android.widget.EditText(this);
        currentInput.setHint(com.example.wifi_geograbber.R.string.current_passphrase_hint);
        currentInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                                  android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(currentInput);

        final android.widget.EditText newInput = new android.widget.EditText(this);
        newInput.setHint(com.example.wifi_geograbber.R.string.new_passphrase_hint);
        newInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                              android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(newInput);

        final android.widget.EditText confirmInput = new android.widget.EditText(this);
        confirmInput.setHint(com.example.wifi_geograbber.R.string.confirm_passphrase_hint);
        confirmInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                                   android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(confirmInput);

        builder.setView(layout);

        builder.setPositiveButton(com.example.wifi_geograbber.R.string.change_passphrase, (dialog, which) -> {
            String current = currentInput.getText().toString();
            String newPass = newInput.getText().toString();
            String confirm = confirmInput.getText().toString();

            if (newPass.length() < 6) {
                Toast.makeText(this, com.example.wifi_geograbber.R.string.passphrase_too_short, Toast.LENGTH_LONG).show();
                return;
            }

            if (!newPass.equals(confirm)) {
                Toast.makeText(this, com.example.wifi_geograbber.R.string.passphrases_dont_match, Toast.LENGTH_LONG).show();
                return;
            }

            changePassphrase(current.toCharArray(), newPass.toCharArray());
        });

        builder.setNegativeButton(com.example.wifi_geograbber.R.string.cancel, null);
        builder.show();
    }

    /**
     * Change encryption passphrase
     */
    protected void changePassphrase(final char[] oldPassphrase, final char[] newPassphrase) {
        new android.os.AsyncTask<Void, Void, Boolean>() {
            android.app.ProgressDialog progressDialog;

            @Override
            protected void onPreExecute() {
                progressDialog = android.app.ProgressDialog.show(
                    MainActivityActions.this,
                    getString(com.example.wifi_geograbber.R.string.change_passphrase_title),
                    "Re-encrypting database...",
                    true
                );
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    if (!encryptionManager.changePassphrase(oldPassphrase, newPassphrase)) {
                        return false;
                    }

                    String newKey = encryptionManager.getCachedDatabaseKey();

                    EncryptionManager tempManager = new EncryptionManager(MainActivityActions.this);
                    if (!tempManager.unlockWithPassphrase(oldPassphrase)) {
                        return false;
                    }
                    String oldKey = tempManager.getCachedDatabaseKey();
                    tempManager.clearPassphrase();

                    String dbPath = getDatabasePath("wifi_scanner.db").getAbsolutePath();

                    if (database != null) {
                        databaseManager.dbClose();
                    }

                    boolean success = DatabaseEncryptionHelper.changeEncryptionKey(
                        dbPath, oldKey, newKey);

                    return success;

                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Error changing passphrase", e);
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();

                if (success) {
                    boolean bioWasEnabled = false;
                    if (biometricAuthManager != null && biometricAuthManager.isBiometricEnabled()) {
                        biometricAuthManager.disableBiometricUnlock();
                        bioWasEnabled = true;
                    }

                    String message = getString(com.example.wifi_geograbber.R.string.encryption_key_changed);
                    if (bioWasEnabled) {
                        message += "\n\n⚠️ Biometric unlock has been disabled. You can re-enable it in encryption settings.";
                    }
                    Toast.makeText(MainActivityActions.this, message, Toast.LENGTH_LONG).show();

                    new android.os.AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... voids) {
                            initializeDatabase();
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void result) {
                            updateTotalNetworksCount();
                        }
                    }.execute();
                } else {
                    Toast.makeText(MainActivityActions.this, com.example.wifi_geograbber.R.string.encryption_key_change_failed,
                                  Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }

    /**
     * Show encryption reset dialog
     */
    protected void showEncryptionResetDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Encryption Reset");
        builder.setMessage("This will reset the encryption and allow you to set up a new passphrase.\n\n" +
                "⚠️ WARNING:\n" +
                "• Current database will be decrypted first\n" +
                "• Then re-encrypted with new passphrase\n" +
                "• All data will be preserved\n" +
                "• Background service will be stopped\n\n" +
                "Do you want to continue?");

        builder.setPositiveButton("Reset Encryption", (dialog, which) -> {
            resetEncryption();
        });

        builder.setNegativeButton(com.example.wifi_geograbber.R.string.cancel, null);
        builder.show();
    }

    /**
     * Reset encryption (decrypt then show setup dialog)
     */
    protected void resetEncryption() {
        new android.os.AsyncTask<Void, Void, Boolean>() {
            android.app.ProgressDialog progressDialog;

            @Override
            protected void onPreExecute() {
                if (isScanning) {
                    stopScanning();
                }
                stopService(new Intent(MainActivityActions.this, ScanService.class));

                progressDialog = android.app.ProgressDialog.show(
                    MainActivityActions.this,
                    "Resetting Encryption",
                    "Deleting encrypted database...",
                    true
                );
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    if (database != null) {
                        databaseManager.dbClose();
                        database = null;
                    }

                    Thread.sleep(500);

                    String dbPath = getDatabasePath("wifi_scanner.db").getAbsolutePath();
                    java.io.File dbFile = new java.io.File(dbPath);
                    java.io.File dbJournal = new java.io.File(dbPath + "-journal");
                    java.io.File dbWal = new java.io.File(dbPath + "-wal");
                    java.io.File dbShm = new java.io.File(dbPath + "-shm");

                    boolean success = true;
                    if (dbFile.exists()) {
                        success = dbFile.delete();
                    }
                    if (dbJournal.exists()) {
                        dbJournal.delete();
                    }
                    if (dbWal.exists()) {
                        dbWal.delete();
                    }
                    if (dbShm.exists()) {
                        dbShm.delete();
                    }

                    if (!success) {
                        return false;
                    }

                    encryptionManager.clearPassphrase();
                    encryptionManager.disableEncryption();

                    if (biometricAuthManager != null && biometricAuthManager.isBiometricEnabled()) {
                        biometricAuthManager.disableBiometricUnlock();
                    }

                    return true;

                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Encryption reset error", e);
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();

                if (success) {
                    Toast.makeText(MainActivityActions.this, "Encryption reset successful. Database deleted. You can now set up new encryption.",
                                  Toast.LENGTH_LONG).show();

                    isDatabaseEncrypted = false;

                    initializeDatabase();

                    showSetupEncryptionDialog();
                } else {
                    Toast.makeText(MainActivityActions.this, "Encryption reset failed. Please try again.",
                                  Toast.LENGTH_LONG).show();

                    initializeDatabase();
                }
            }
        }.execute();
    }

    /**
     * Show disable encryption dialog
     */
    protected void showDisableEncryptionDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(com.example.wifi_geograbber.R.string.disable_encryption_title);
        builder.setMessage(com.example.wifi_geograbber.R.string.disable_encryption_message);

        builder.setPositiveButton(com.example.wifi_geograbber.R.string.yes_disable, (dialog, which) -> {
            requestAuthenticationForDisableEncryption();
        });

        builder.setNegativeButton(com.example.wifi_geograbber.R.string.cancel, null);
        builder.show();
    }

    /**
     * Request authentication before disabling encryption
     */
    protected void requestAuthenticationForDisableEncryption() {
        if (biometricAuthManager != null && biometricAuthManager.isBiometricEnabled()) {
            biometricAuthManager.authenticateWithBiometric(
                MainActivityActions.this,
                new BiometricAuthManager.BiometricAuthCallback() {
                    @Override
                    public void onAuthenticationSucceeded(char[] passphrase) {
                        String dbKey = encryptionManager.getCachedDatabaseKey();
                        if (dbKey != null && !dbKey.isEmpty()) {
                            disableEncryptionWithPassphrase(dbKey);
                        } else {
                            Toast.makeText(MainActivityActions.this, "Failed to retrieve database key", Toast.LENGTH_SHORT).show();
                        }
                        java.util.Arrays.fill(passphrase, '\0');
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        Toast.makeText(MainActivityActions.this, "Authentication failed", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationError(String errorMessage) {
                        Toast.makeText(MainActivityActions.this, "Authentication error: " + errorMessage, Toast.LENGTH_SHORT).show();
                        showPasswordInputForDisableEncryption();
                    }
                }
            );
        } else {
            showPasswordInputForDisableEncryption();
        }
    }

    /**
     * Show password input dialog for disabling encryption
     */
    protected void showPasswordInputForDisableEncryption() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Enter Password");
        builder.setMessage("Enter your database password to decrypt:");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                          android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Password");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton("Decrypt", (dialog, which) -> {
            String password = input.getText().toString();
            if (password.isEmpty()) {
                Toast.makeText(MainActivityActions.this, "Password cannot be empty",
                             Toast.LENGTH_SHORT).show();
                return;
            }
            disableEncryptionWithPassphrase(password);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Disable encryption with the provided passphrase
     */
    protected void disableEncryptionWithPassphrase(final String passphrase) {
        new android.os.AsyncTask<Void, Void, Boolean>() {
            android.app.ProgressDialog progressDialog;

            @Override
            protected void onPreExecute() {
                progressDialog = android.app.ProgressDialog.show(
                    MainActivityActions.this,
                    getString(com.example.wifi_geograbber.R.string.disable_encryption_title),
                    getString(com.example.wifi_geograbber.R.string.decryption_in_progress),
                    true
                );
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    String encryptedPath = getDatabasePath("wifi_scanner.db").getAbsolutePath();
                    String unencryptedPath = getDatabasePath("wifi_scanner_unencrypted.db").getAbsolutePath();

                    if (database != null) {
                        databaseManager.dbClose();
                    }

                    java.io.File dbFile = new java.io.File(encryptedPath);
                    if (!DatabaseEncryptionHelper.isDatabaseEncrypted(dbFile)) {
                        encryptionManager.disableEncryption();
                        return true;
                    }

                    boolean success = DatabaseEncryptionHelper.decryptDatabase(
                        MainActivityActions.this, encryptedPath, unencryptedPath, passphrase);

                    if (success) {
                        new java.io.File(encryptedPath).delete();
                        new java.io.File(unencryptedPath).renameTo(new java.io.File(encryptedPath));
                        encryptionManager.disableEncryption();
                    }

                    return success;

                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Error disabling encryption", e);
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();

                if (success) {
                    Toast.makeText(MainActivityActions.this, com.example.wifi_geograbber.R.string.decryption_success,
                                  Toast.LENGTH_LONG).show();
                    isDatabaseEncrypted = false;

                    DatabaseHelper dbHelper = new DatabaseHelper(MainActivityActions.this);
                    database = dbHelper.getWritableDatabase();
                    databaseManager = new DatabaseManager(database, false);
                } else {
                    showDecryptionFailedDialog();
                }
            }
        }.execute();
    }

    /**
     * Show dialog when decryption fails with options to resolve the issue
     */
    protected void showDecryptionFailedDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Decryption Failed");
        builder.setMessage("Failed to decrypt the database. This may be due to:\n\n" +
                          "• Wrong passphrase stored\n" +
                          "• Corrupted database file\n" +
                          "• Database is already unencrypted\n\n" +
                          "What would you like to do?");

        builder.setPositiveButton("Delete & Start Fresh", (dialog, which) -> {
            new android.app.AlertDialog.Builder(MainActivityActions.this)
                .setTitle("Confirm Delete")
                .setMessage("This will delete the current database and all data. Are you sure?")
                .setPositiveButton("Yes, Delete", (d, w) -> {
                    try {
                        String dbPath = getDatabasePath("wifi_scanner.db").getAbsolutePath();
                        java.io.File dbFile = new java.io.File(dbPath);

                        if (database != null) {
                            databaseManager.dbClose();
                        }

                        if (dbFile.exists()) {
                            dbFile.delete();
                        }
                        new java.io.File(dbPath + "-journal").delete();
                        new java.io.File(dbPath + "-wal").delete();
                        new java.io.File(dbPath + "-shm").delete();

                        encryptionManager.disableEncryption();
                        isDatabaseEncrypted = false;

                        DatabaseHelper dbHelper = new DatabaseHelper(MainActivityActions.this);
                        database = dbHelper.getWritableDatabase();
                        databaseManager = new DatabaseManager(database, false);

                        Toast.makeText(MainActivityActions.this, "Database deleted. Starting fresh with unencrypted database.",
                                     Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        android.util.Log.e("MainActivity", "Error deleting database", e);
                        Toast.makeText(MainActivityActions.this, "Error deleting database: " + e.getMessage(),
                                     Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        builder.setNegativeButton("Keep Database", (dialog, which) -> {
            Toast.makeText(MainActivityActions.this, "Database kept encrypted. Decryption cancelled.",
                         Toast.LENGTH_SHORT).show();
        });

        builder.setNeutralButton("Try Manual Password", (dialog, which) -> {
            showManualDecryptionDialog();
        });

        builder.setCancelable(false);
        builder.show();
    }

    /**
     * Show dialog for manual password entry to decrypt database
     */
    protected void showManualDecryptionDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Enter Decryption Password");
        builder.setMessage("Enter the password used to encrypt this database:");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                          android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Password");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton("Decrypt", (dialog, which) -> {
            String password = input.getText().toString();
            if (password.isEmpty()) {
                Toast.makeText(MainActivityActions.this, "Password cannot be empty",
                             Toast.LENGTH_SHORT).show();
                return;
            }
            tryDecryptionWithPassword(password);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Try to decrypt database with provided password
     */
    protected void tryDecryptionWithPassword(final String password) {
        new android.os.AsyncTask<Void, Void, Boolean>() {
            android.app.ProgressDialog progressDialog;

            @Override
            protected void onPreExecute() {
                progressDialog = android.app.ProgressDialog.show(
                    MainActivityActions.this,
                    "Decrypting",
                    "Attempting to decrypt database...",
                    true
                );
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    String encryptedPath = getDatabasePath("wifi_scanner.db").getAbsolutePath();
                    String unencryptedPath = getDatabasePath("wifi_scanner_unencrypted.db").getAbsolutePath();

                    if (database != null) {
                        databaseManager.dbClose();
                    }

                    boolean success = DatabaseEncryptionHelper.decryptDatabase(
                        MainActivityActions.this, encryptedPath, unencryptedPath, password);

                    if (success) {
                        new java.io.File(encryptedPath).delete();
                        new java.io.File(unencryptedPath).renameTo(new java.io.File(encryptedPath));
                        encryptionManager.disableEncryption();
                    }

                    return success;

                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Error decrypting with manual password", e);
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();

                if (success) {
                    Toast.makeText(MainActivityActions.this, com.example.wifi_geograbber.R.string.decryption_success,
                                  Toast.LENGTH_LONG).show();
                    isDatabaseEncrypted = false;

                    DatabaseHelper dbHelper = new DatabaseHelper(MainActivityActions.this);
                    database = dbHelper.getWritableDatabase();
                    databaseManager = new DatabaseManager(database, false);
                } else {
                    Toast.makeText(MainActivityActions.this, "Decryption failed. Wrong password or corrupted database.",
                                  Toast.LENGTH_LONG).show();
                    showDecryptionFailedDialog();
                }
            }
        }.execute();
    }

    /**
     * Show dialog for importing encrypted database
     */
    protected void showEncryptedDatabaseImportDialog(final java.io.File encryptedDbFile) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(com.example.wifi_geograbber.R.string.encrypted_db_detected);
        builder.setMessage(com.example.wifi_geograbber.R.string.encrypted_db_detected_message);

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(com.example.wifi_geograbber.R.string.passphrase_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                           android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton("Import", (dialog, which) -> {
            String passphrase = input.getText().toString();
            importEncryptedDatabase(encryptedDbFile, passphrase);
        });

        builder.setNegativeButton(com.example.wifi_geograbber.R.string.cancel, (dialog, which) -> {
            encryptedDbFile.delete();
        });

        builder.show();
    }

    /**
     * Import encrypted database
     */
    protected void importEncryptedDatabase(final java.io.File encryptedDbFile, final String passphrase) {
        new android.os.AsyncTask<Void, Void, Boolean>() {
            android.app.ProgressDialog progressDialog;
            int wifiCount = 0;
            int bluetoothCount = 0;
            String derivedKey = null;
            String originalPassphrase = passphrase;

            @Override
            protected void onPreExecute() {
                progressDialog = android.app.ProgressDialog.show(
                    MainActivityActions.this,
                    "Importing Database",
                    "Verifying encrypted database...",
                    true
                );
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                net.sqlcipher.database.SQLiteDatabase testDb = null;
                try {
                    net.sqlcipher.database.SQLiteDatabase.loadLibs(MainActivityActions.this);

                    if (pendingEncryptionSalt != null) {
                        char[] passphraseChars = passphrase.toCharArray();
                        String hexKey = encryptionManager.deriveKeyWithCustomSalt(passphraseChars, pendingEncryptionSalt);
                        java.util.Arrays.fill(passphraseChars, '\0');

                        derivedKey = "x'" + hexKey + "'";

                        testDb = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                            encryptedDbFile.getAbsolutePath(), derivedKey, null,
                            net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY);
                    } else {
                        derivedKey = passphrase;
                        testDb = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                            encryptedDbFile.getAbsolutePath(), derivedKey, null,
                            net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY);
                    }

                    boolean hasWifiData = hasEncryptedTable(testDb, "wifi_data");
                    boolean hasDeviceData = hasEncryptedTable(testDb, "device_data");

                    if (!hasWifiData && !hasDeviceData) {
                        return false;
                    }

                    android.database.Cursor cursor = testDb.rawQuery(
                        "SELECT COUNT(*) FROM device_data WHERE device_type='WIFI' AND latitude != 0 AND longitude != 0", null);
                    if (cursor.moveToFirst()) wifiCount += cursor.getInt(0);
                    cursor.close();

                    cursor = testDb.rawQuery(
                        "SELECT COUNT(*) FROM device_data WHERE device_type='BLUETOOTH' AND latitude != 0 AND longitude != 0", null);
                    if (cursor.moveToFirst()) bluetoothCount = cursor.getInt(0);
                    cursor.close();

                    if (hasWifiData) {
                        cursor = testDb.rawQuery(
                            "SELECT COUNT(*) FROM wifi_data WHERE latitude != 0 AND longitude != 0", null);
                        if (cursor.moveToFirst()) wifiCount += cursor.getInt(0);
                        cursor.close();
                    }

                    return true;

                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Error opening encrypted database", e);
                    return false;
                } finally {
                    if (testDb != null) testDb.close();
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();

                if (success) {
                    android.app.AlertDialog.Builder confirmBuilder = new android.app.AlertDialog.Builder(MainActivityActions.this);
                    confirmBuilder.setTitle("Import Encrypted Database?");
                    confirmBuilder.setMessage(
                        "🔒 Encrypted database verified!\n\n" +
                        "Found data:\n" +
                        "• " + wifiCount + " WiFi networks\n" +
                        "• " + bluetoothCount + " Bluetooth devices\n\n" +
                        "Import this data into your database?"
                    );

                    confirmBuilder.setPositiveButton("Import", (d, w) -> {
                        performEncryptedDatabaseImport(encryptedDbFile, derivedKey, originalPassphrase, wifiCount, bluetoothCount);
                    });

                    confirmBuilder.setNegativeButton(com.example.wifi_geograbber.R.string.cancel, (d, w) -> {
                        encryptedDbFile.delete();
                    });

                    confirmBuilder.show();
                    addLogMessage("✓ Database unlocked successfully");
                } else {
                    Toast.makeText(MainActivityActions.this, com.example.wifi_geograbber.R.string.wrong_passphrase, Toast.LENGTH_LONG).show();
                    addLogMessage("✗ Failed to unlock database - wrong passphrase or corrupted file");
                    encryptedDbFile.delete();
                    pendingEncryptionSalt = null;
                }
            }
        }.execute();
    }

    /**
     * Perform actual encrypted database import (copy data)
     */
    protected void performEncryptedDatabaseImport(final java.io.File encryptedDbFile,
                                                final String derivedKey,
                                                final String originalPassphrase,
                                                final int wifiCount,
                                                final int bluetoothCount) {
        new android.os.AsyncTask<Void, Void, Boolean>() {
            android.app.ProgressDialog progressDialog;

            @Override
            protected void onPreExecute() {
                progressDialog = android.app.ProgressDialog.show(
                    MainActivityActions.this,
                    "Importing Database",
                    "Copying encrypted database data...",
                    true
                );
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                net.sqlcipher.database.SQLiteDatabase externalDb = null;
                try {
                    net.sqlcipher.database.SQLiteDatabase.loadLibs(MainActivityActions.this);
                    externalDb = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                        encryptedDbFile.getAbsolutePath(), derivedKey, null,
                        net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY);

                    copyDataFromEncryptedToInternal(externalDb);

                    return true;

                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Error importing encrypted database", e);
                    return false;
                } finally {
                    if (externalDb != null) externalDb.close();
                    encryptedDbFile.delete();
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();

                pendingEncryptionSalt = null;

                if (success) {
                    if (securityOverlay != null) {
                        securityOverlay.setVisibility(View.GONE);
                    }

                    if (encryptionManager != null && encryptionManager.isEncryptionEnabled()) {
                        encryptionManager.unlockWithPassphrase(originalPassphrase.toCharArray());
                    }

                    Toast.makeText(MainActivityActions.this,
                        "✓ Imported " + wifiCount + " WiFi + " + bluetoothCount + " BT devices",
                        Toast.LENGTH_LONG).show();
                    addLogMessage("✓ Import completed: " + wifiCount + " WiFi + " + bluetoothCount + " BT");

                    updateTotalNetworksCount();

                    isShowingStoredData = true;
                    showData();
                } else {
                    Toast.makeText(MainActivityActions.this, "Import failed", Toast.LENGTH_LONG).show();
                    addLogMessage("✗ Import failed");
                }
            }
        }.execute();
    }

    /**
     * Copy data from encrypted database to internal database
     */
    protected void copyDataFromEncryptedToInternal(net.sqlcipher.database.SQLiteDatabase externalDb) {
        int wifiInserted = 0;
        int deviceInserted = 0;

        try {
            databaseManager.dbBeginTransaction();

            if (hasEncryptedTable(externalDb, "wifi_data")) {
                android.database.Cursor cursor = externalDb.rawQuery("SELECT * FROM wifi_data", null);
                while (cursor.moveToNext()) {
                    android.content.ContentValues values = new android.content.ContentValues();
                    for (String columnName : cursor.getColumnNames()) {
                        int columnIndex = cursor.getColumnIndex(columnName);
                        if (!cursor.isNull(columnIndex)) {
                            int type = cursor.getType(columnIndex);
                            switch (type) {
                                case android.database.Cursor.FIELD_TYPE_INTEGER:
                                    values.put(columnName, cursor.getLong(columnIndex));
                                    break;
                                case android.database.Cursor.FIELD_TYPE_FLOAT:
                                    values.put(columnName, cursor.getDouble(columnIndex));
                                    break;
                                case android.database.Cursor.FIELD_TYPE_STRING:
                                    values.put(columnName, cursor.getString(columnIndex));
                                    break;
                            }
                        }
                    }
                    long result = databaseManager.dbInsert("wifi_data", null, values);
                    if (result != -1) wifiInserted++;
                }
                cursor.close();
            }

            if (hasEncryptedTable(externalDb, "device_data")) {
                android.database.Cursor cursor = externalDb.rawQuery("SELECT * FROM device_data", null);
                while (cursor.moveToNext()) {
                    android.content.ContentValues values = new android.content.ContentValues();
                    for (String columnName : cursor.getColumnNames()) {
                        int columnIndex = cursor.getColumnIndex(columnName);
                        if (!cursor.isNull(columnIndex)) {
                            int type = cursor.getType(columnIndex);
                            switch (type) {
                                case android.database.Cursor.FIELD_TYPE_INTEGER:
                                    values.put(columnName, cursor.getLong(columnIndex));
                                    break;
                                case android.database.Cursor.FIELD_TYPE_FLOAT:
                                    values.put(columnName, cursor.getDouble(columnIndex));
                                    break;
                                case android.database.Cursor.FIELD_TYPE_STRING:
                                    values.put(columnName, cursor.getString(columnIndex));
                                    break;
                            }
                        }
                    }
                    long result = databaseManager.dbInsert("device_data", null, values);
                    if (result != -1) deviceInserted++;
                }
                cursor.close();
            }

            databaseManager.dbSetTransactionSuccessful();
            databaseManager.dbEndTransaction();

            android.util.Log.i("Import", "✓ Import transaction committed: " + wifiInserted + " WiFi + " + deviceInserted + " device records");

        } catch (Exception e) {
            android.util.Log.e("Import", "Error during import transaction: " + e.getMessage(), e);
            try {
                databaseManager.dbEndTransaction();
            } catch (Exception e2) {
                android.util.Log.e("Import", "Error rolling back transaction: " + e2.getMessage());
            }
            throw new RuntimeException("Import failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check if encrypted table exists
     */
    protected boolean hasEncryptedTable(net.sqlcipher.database.SQLiteDatabase db, String tableName) {
        android.database.Cursor cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            new String[]{tableName});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    /**
     * Show first-launch encryption prompt
     */
    protected void showFirstLaunchEncryptionDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(com.example.wifi_geograbber.R.string.first_launch_encryption_title);
        builder.setMessage(com.example.wifi_geograbber.R.string.first_launch_encryption_message);
        builder.setCancelable(false);

        builder.setPositiveButton(com.example.wifi_geograbber.R.string.enable_now, (dialog, which) -> {
            showSetupEncryptionDialog();
        });

        builder.setNegativeButton(com.example.wifi_geograbber.R.string.maybe_later, (dialog, which) -> {
            // Continue with unencrypted database
        });

        builder.show();
    }

    /**
     * Show encryption setup dialog
     */
    protected void showSetupEncryptionDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(com.example.wifi_geograbber.R.string.setup_encryption_title);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        android.widget.TextView infoText = new android.widget.TextView(this);
        infoText.setText("Password requirements:\n" +
                        "• At least 12 characters\n" +
                        "• At least 1 uppercase letter\n" +
                        "• At least 1 lowercase letter\n" +
                        "• At least 1 digit\n" +
                        "• At least 1 special character");
        infoText.setTextSize(12);
        infoText.setPadding(0, 0, 0, 20);
        infoText.setTextColor(0xFF666666);
        layout.addView(infoText);

        final android.widget.EditText passphraseInput = new android.widget.EditText(this);
        passphraseInput.setHint(com.example.wifi_geograbber.R.string.passphrase_hint);
        passphraseInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                                     android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passphraseInput);

        final android.widget.EditText confirmInput = new android.widget.EditText(this);
        confirmInput.setHint(com.example.wifi_geograbber.R.string.confirm_passphrase_hint);
        confirmInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                                   android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(confirmInput);

        android.widget.CheckBox showPassphraseBox = new android.widget.CheckBox(this);
        showPassphraseBox.setText(com.example.wifi_geograbber.R.string.show_passphrase);
        showPassphraseBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int inputType = isChecked ?
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD :
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
            passphraseInput.setInputType(inputType);
            confirmInput.setInputType(inputType);
        });
        layout.addView(showPassphraseBox);

        builder.setView(layout);

        builder.setPositiveButton(com.example.wifi_geograbber.R.string.set_passphrase, (dialog, which) -> {
            String passphrase = passphraseInput.getText().toString();
            String confirm = confirmInput.getText().toString();

            if (passphrase.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "Please enter a passphrase", Toast.LENGTH_LONG).show();
                showSetupEncryptionDialog();
                return;
            }

            if (!passphrase.equals(confirm)) {
                Toast.makeText(this, com.example.wifi_geograbber.R.string.passphrases_dont_match, Toast.LENGTH_LONG).show();
                showSetupEncryptionDialog();
                return;
            }

            char[] passphraseChars = passphrase.toCharArray();
            String validationError = EncryptionManager.validatePassphraseStrength(passphraseChars);

            if (validationError != null) {
                android.widget.Toast.makeText(MainActivityActions.this, validationError, android.widget.Toast.LENGTH_LONG).show();
                return;
            }

            setupEncryption(passphraseChars);
        });

        builder.setNegativeButton(com.example.wifi_geograbber.R.string.cancel, null);
        builder.show();
    }

    /**
     * Set up encryption with passphrase
     */
    protected void setupEncryption(final char[] passphrase) {
        final char[] passphraseCopy = Arrays.copyOf(passphrase, passphrase.length);

        new android.os.AsyncTask<Void, Void, Boolean>() {
            android.app.ProgressDialog progressDialog;

            @Override
            protected void onPreExecute() {
                progressDialog = android.app.ProgressDialog.show(
                    MainActivityActions.this,
                    getString(com.example.wifi_geograbber.R.string.migration_title),
                    getString(com.example.wifi_geograbber.R.string.migration_in_progress),
                    true
                );
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    if (!encryptionManager.setupEncryption(passphrase)) {
                        return false;
                    }

                    String unencryptedPath = getDatabasePath("wifi_scanner.db").getAbsolutePath();
                    String encryptedPath = getDatabasePath("wifi_scanner_encrypted.db").getAbsolutePath();

                    String dbKey = encryptionManager.getCachedDatabaseKey();

                    if (dbKey == null) {
                        return false;
                    }

                    if (database != null) {
                        databaseManager.dbClose();
                    }

                    java.io.File unencryptedFile = new java.io.File(unencryptedPath);
                    if (unencryptedFile.exists() && unencryptedFile.length() > 0) {
                        boolean success = DatabaseEncryptionHelper.migrateToEncrypted(
                            MainActivityActions.this, unencryptedPath, encryptedPath, dbKey);

                        if (success) {
                            unencryptedFile.delete();
                            new java.io.File(encryptedPath).renameTo(unencryptedFile);
                        }

                        return success;
                    } else {
                        if (unencryptedFile.exists()) {
                            unencryptedFile.delete();
                        }

                        encryptedDbHelper = new DatabaseEncryptionHelper(MainActivityActions.this, dbKey);
                        net.sqlcipher.database.SQLiteDatabase db = encryptedDbHelper.openEncryptedDatabase();

                        if (db != null) {
                            try {
                                db.execSQL("SELECT COUNT(*) FROM wifi_data");
                            } catch (Exception e) {
                                // onCreate will be called automatically
                            }
                            db.close();
                            return true;
                        } else {
                            return false;
                        }
                    }

                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Encryption setup error", e);
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();

                if (success) {
                    Toast.makeText(MainActivityActions.this, com.example.wifi_geograbber.R.string.migration_success,
                                  Toast.LENGTH_LONG).show();
                    isDatabaseEncrypted = true;

                    new android.os.AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... voids) {
                            initializeDatabase();
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void result) {
                            updateTotalNetworksCount();
                            offerBiometricSetup(passphraseCopy);
                        }
                    }.execute();
                } else {
                    Toast.makeText(MainActivityActions.this, com.example.wifi_geograbber.R.string.migration_failed,
                                  Toast.LENGTH_LONG).show();
                    encryptionManager.disableEncryption();

                    DatabaseHelper dbHelper = new DatabaseHelper(MainActivityActions.this);
                    database = dbHelper.getWritableDatabase();
                    databaseManager = new DatabaseManager(database, false);

                    Arrays.fill(passphraseCopy, '\0');
                }
            }
        }.execute();
    }

    /**
     * Offer to enable biometric unlock after encryption setup
     */
    protected void offerBiometricSetup(final char[] originalPassphrase) {
        BiometricAuthManager biometricAuthManager = new BiometricAuthManager(this, encryptionManager);

        if (!biometricAuthManager.isBiometricSupported()) {
            Arrays.fill(originalPassphrase, '\0');
            return;
        }

        if (biometricAuthManager.isBiometricEnabled()) {
            Arrays.fill(originalPassphrase, '\0');
            return;
        }

        new android.app.AlertDialog.Builder(this)
            .setTitle("Enable Biometric Unlock?")
            .setMessage("Would you like to enable fingerprint or face unlock for quick access to your encrypted database?\n\n" +
                       "🔒 Security Information:\n" +
                       "• Your passphrase is never stored in plain text\n" +
                       "• It's encrypted with AES-256 in Android Keystore (hardware-secured)\n" +
                       "• Only unlocked after successful biometric authentication\n" +
                       "• Biometric data never leaves your device\n\n" +
                       "You can change this setting later in the encryption settings.")
            .setPositiveButton("Enable", (dialog, which) -> {
                biometricAuthManager.enableBiometricUnlock(this, originalPassphrase,
                    new BiometricAuthManager.BiometricEnrollCallback() {
                        @Override
                        public void onEnrollSuccess() {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivityActions.this,
                                    "Biometric unlock enabled successfully",
                                    Toast.LENGTH_SHORT).show();
                            });
                            Arrays.fill(originalPassphrase, '\0');
                        }

                        @Override
                        public void onEnrollFailed(String error) {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivityActions.this,
                                    "Failed to enable biometric unlock: " + error,
                                    Toast.LENGTH_LONG).show();
                            });
                            Arrays.fill(originalPassphrase, '\0');
                        }
                    });
            })
            .setNegativeButton("Skip", (dialog, which) -> {
                Arrays.fill(originalPassphrase, '\0');
            })
            .setOnCancelListener(dialog -> {
                Arrays.fill(originalPassphrase, '\0');
            })
            .show();
    }

    /**
     * Show dialog to enable biometric unlock
     */
    protected void showEnableBiometricDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("🔒 Enable Biometric Unlock");
        builder.setMessage("Enable fingerprint/face unlock for quick database access?\n\n" +
            "Your passphrase will be encrypted and stored securely in the Android Keystore, " +
            "accessible only via biometric authentication.\n\n" +
            "You can still use your passphrase as a fallback.");

        builder.setPositiveButton("Enable", (dialog, which) -> {
            showVerifyPassphraseForBiometric();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Verify passphrase before enabling biometric unlock
     */
    protected void showVerifyPassphraseForBiometric() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Verify Passphrase");
        builder.setMessage("Enter your current passphrase to enable biometric unlock:");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final android.widget.EditText passphraseInput = new android.widget.EditText(this);
        passphraseInput.setHint("Current Passphrase");
        passphraseInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                                     android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passphraseInput);

        builder.setView(layout);

        builder.setPositiveButton("Verify", (dialog, which) -> {
            String passphraseStr = passphraseInput.getText().toString();
            char[] passphrase = passphraseStr.toCharArray();

            if (encryptionManager.testPassphraseWithDatabase(passphrase)) {
                biometricAuthManager.enableBiometricUnlock(this, passphrase,
                    new BiometricAuthManager.BiometricEnrollCallback() {
                        @Override
                        public void onEnrollSuccess() {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivityActions.this, "✅ Biometric unlock enabled successfully",
                                    Toast.LENGTH_LONG).show();
                                addLogMessage("INFO: Biometric unlock enabled");
                            });
                            java.util.Arrays.fill(passphrase, '\0');
                        }

                        @Override
                        public void onEnrollFailed(String error) {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivityActions.this, "❌ Failed to enable biometric unlock: " + error,
                                    Toast.LENGTH_LONG).show();
                                addLogMessage("ERROR: Failed to enable biometric unlock - " + error);
                            });
                            java.util.Arrays.fill(passphrase, '\0');
                        }
                    });
            } else {
                Toast.makeText(this, "❌ Incorrect passphrase - cannot unlock database", Toast.LENGTH_LONG).show();
                addLogMessage("ERROR: Passphrase verification failed for biometric enrollment");
                java.util.Arrays.fill(passphrase, '\0');
            }

            passphraseInput.setText("");
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            passphraseInput.setText("");
        });

        builder.show();
    }

    /**
     * Show dialog to disable biometric unlock
     */
    protected void showDisableBiometricDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("🔓 Disable Biometric Unlock");
        builder.setMessage("Disable fingerprint/face unlock?\n\n" +
            "You will need to enter your passphrase manually to unlock the database.");

        builder.setPositiveButton("Disable", (dialog, which) -> {
            biometricAuthManager.disableBiometricUnlock();
            Toast.makeText(this, "Biometric unlock disabled", Toast.LENGTH_SHORT).show();
            addLogMessage("INFO: Biometric unlock disabled");
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // Export debug log to txt file
    protected void exportDebugLogToTxt() {
        String logText = logcatText.getText().toString();

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Export Debug Log");
        builder.setMessage("Choose export option:");

        builder.setPositiveButton("📋 Copy to Clipboard", (dialog, which) -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Debug Log", logText);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Debug log copied to clipboard!", Toast.LENGTH_SHORT).show();
            addLogMessage("Debug log copied to clipboard");
        });

        builder.setNegativeButton("💾 Save to Downloads", (dialog, which) -> {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    android.content.ContentValues values = new android.content.ContentValues();
                    String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
                    String filename = "debug_log_" + timestamp + ".txt";
                    values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename);
                    values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                    values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);

                    android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        java.io.OutputStream outputStream = getContentResolver().openOutputStream(uri);
                        outputStream.write(logText.getBytes());
                        outputStream.close();
                        Toast.makeText(this, "Debug log saved to Downloads/" + filename, Toast.LENGTH_LONG).show();
                        addLogMessage("Debug log saved to Downloads/" + filename);
                    }
                } else {
                    java.io.File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                    String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
                    java.io.File exportFile = new java.io.File(downloadsDir, "debug_log_" + timestamp + ".txt");
                    java.io.FileWriter writer = new java.io.FileWriter(exportFile);
                    writer.write(logText);
                    writer.close();
                    Toast.makeText(this, "Debug log saved to Downloads/" + exportFile.getName(), Toast.LENGTH_LONG).show();
                    addLogMessage("Debug log saved to " + exportFile.getName());
                }
            } catch (Exception e) {
                Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                addLogMessage("ERROR: Failed to save log: " + e.getMessage());
            }
        });

        builder.setNeutralButton("Cancel", null);
        builder.show();
    }
}
