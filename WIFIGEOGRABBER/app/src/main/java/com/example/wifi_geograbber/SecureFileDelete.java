package com.example.wifi_geograbber;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.security.SecureRandom;

/**
 * SecureFileDelete - Securely delete files by overwriting with random data
 *
 * Security Features:
 * - Overwrites file content multiple times with random data before deletion
 * - Prevents data recovery through forensic analysis
 * - Handles file size and permissions properly
 *
 * Usage:
 * SecureFileDelete.secureDelete(file);
 */
public class SecureFileDelete {
    private static final String TAG = "SecureFileDelete";
    private static final int OVERWRITE_PASSES = 3; // Number of overwrite passes (DoD 5220.22-M standard uses 3)
    private static final int BUFFER_SIZE = 8192; // 8KB buffer for overwriting

    /**
     * Securely delete a file by overwriting with random data multiple times
     * @param file File to delete
     * @return true if successfully deleted
     */
    public static boolean secureDelete(File file) {
        if (file == null || !file.exists()) {
            return false;
        }

        if (!file.isFile()) {
            Log.w(TAG, "Cannot securely delete non-file: " + file.getName());
            return false;
        }

        try {
            long fileSize = file.length();

            if (fileSize == 0) {
                // Empty file, just delete
                return file.delete();
            }

            // Overwrite file multiple times with random data
            SecureRandom random = new SecureRandom();

            for (int pass = 0; pass < OVERWRITE_PASSES; pass++) {
                try (RandomAccessFile raf = new RandomAccessFile(file, "rws")) {
                    raf.seek(0);

                    long remaining = fileSize;
                    byte[] buffer = new byte[BUFFER_SIZE];

                    while (remaining > 0) {
                        int bytesToWrite = (int) Math.min(BUFFER_SIZE, remaining);
                        random.nextBytes(buffer);
                        raf.write(buffer, 0, bytesToWrite);
                        remaining -= bytesToWrite;
                    }

                    // Force write to disk
                    raf.getFD().sync();
                }
            }

            // Finally, delete the file
            boolean deleted = file.delete();

            if (deleted) {
                Log.i(TAG, "File securely deleted");
            } else {
                Log.w(TAG, "File overwritten but delete failed");
            }

            return deleted;

        } catch (Exception e) {
            Log.e(TAG, "Error securely deleting file", e);
            // Try normal delete as fallback
            return file.delete();
        }
    }

    /**
     * Securely delete multiple files
     * @param files Array of files to delete
     * @return true if all files were deleted successfully
     */
    public static boolean secureDelete(File... files) {
        if (files == null || files.length == 0) {
            return false;
        }

        boolean allDeleted = true;
        for (File file : files) {
            if (!secureDelete(file)) {
                allDeleted = false;
            }
        }
        return allDeleted;
    }

    /**
     * Securely delete database and all associated files
     * @param dbFile Main database file
     * @return true if all files were deleted
     */
    public static boolean secureDatabaseDelete(File dbFile) {
        if (dbFile == null) {
            return false;
        }

        boolean success = true;

        // Delete main database file
        success &= secureDelete(dbFile);

        // Delete associated files
        File journalFile = new File(dbFile.getAbsolutePath() + "-journal");
        if (journalFile.exists()) {
            success &= secureDelete(journalFile);
        }

        File walFile = new File(dbFile.getAbsolutePath() + "-wal");
        if (walFile.exists()) {
            success &= secureDelete(walFile);
        }

        File shmFile = new File(dbFile.getAbsolutePath() + "-shm");
        if (shmFile.exists()) {
            success &= secureDelete(shmFile);
        }

        return success;
    }
}
