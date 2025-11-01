package com.example.wifi_geograbber;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * EncryptionManager - Manages database encryption using Android Keystore
 * 
 * Features:
 * - Secure passphrase storage using Android Keystore
 * - AES-256 encryption for database passphrases
 * - In-memory passphrase caching for app session
 * - Secure memory clearing on app close
 * 
 * Security Design:
 * - User passphrase never stored in plaintext
 * - Keystore-protected encryption keys
 * - GCM mode for authenticated encryption
 * - Salt-based passphrase derivation
 */
public class EncryptionManager {
    private static final String TAG = "EncryptionManager";
    private static final String PREFS_NAME = "encryption_prefs";
    private static final String KEY_ALIAS = "geograbber_db_key";
    private static final String KEY_ENCRYPTED_PASSPHRASE = "encrypted_passphrase";
    private static final String KEY_PASSPHRASE_HASH = "passphrase_hash";
    private static final String KEY_ENCRYPTION_ENABLED = "encryption_enabled";
    private static final String KEY_SALT = "passphrase_salt";
    private static final String KEY_IV = "encryption_iv";
    
    private static final int GCM_TAG_LENGTH = 128;
    
    private final Context context;
    private final SharedPreferences prefs;
    private static char[] cachedPassphrase = null;  // Static so it's shared across all instances
    
    public EncryptionManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Check if database encryption is enabled
     */
    public boolean isEncryptionEnabled() {
        return prefs.getBoolean(KEY_ENCRYPTION_ENABLED, false);
    }
    
    /**
     * Check if passphrase is set (encryption configured)
     */
    public boolean isPassphraseSet() {
        return prefs.contains(KEY_ENCRYPTED_PASSPHRASE) && 
               prefs.contains(KEY_PASSPHRASE_HASH);
    }
    
    /**
     * Check if passphrase is currently cached in memory
     */
    public boolean isPassphraseCached() {
        return cachedPassphrase != null && cachedPassphrase.length > 0;
    }
    
    /**
     * Set up encryption with a new passphrase
     * @param passphrase User's chosen passphrase (will be cleared after use)
     * @return true if setup successful
     */
    public boolean setupEncryption(char[] passphrase) {
        if (passphrase == null || passphrase.length < 6) {
            Log.e(TAG, "Passphrase too short (minimum 6 characters)");
            return false;
        }
        
        try {
            // Generate salt for passphrase derivation
            byte[] salt = generateSalt();
            
            // Derive database key from passphrase
            String dbKey = deriveKey(passphrase, salt);
            
            // Calculate hash for verification
            String passphraseHash = hashPassphrase(passphrase, salt);
            
            // Encrypt the database key using Android Keystore
            String encryptedKey = encryptData(dbKey, salt);
            
            if (encryptedKey == null) {
                Log.e(TAG, "Failed to encrypt passphrase");
                return false;
            }
            
            // Store encrypted data
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_ENCRYPTED_PASSPHRASE, encryptedKey);
            editor.putString(KEY_PASSPHRASE_HASH, passphraseHash);
            editor.putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP));
            editor.putBoolean(KEY_ENCRYPTION_ENABLED, true);
            editor.apply();
            
            // Cache the passphrase
            cachePassphrase(passphrase);
            
            Log.i(TAG, "Encryption setup successful");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up encryption", e);
            return false;
        }
    }
    
    /**
     * Verify and unlock with passphrase
     * @param passphrase User-entered passphrase
     * @return true if passphrase is correct
     */
    public boolean unlockWithPassphrase(char[] passphrase) {
        if (!isPassphraseSet()) {
            Log.e(TAG, "No passphrase configured");
            return false;
        }
        
        try {
            // Get stored salt and hash
            String saltStr = prefs.getString(KEY_SALT, null);
            String storedHash = prefs.getString(KEY_PASSPHRASE_HASH, null);
            
            if (saltStr == null || storedHash == null) {
                Log.e(TAG, "Missing encryption data");
                return false;
            }
            
            byte[] salt = Base64.decode(saltStr, Base64.NO_WRAP);
            
            // Verify passphrase
            String inputHash = hashPassphrase(passphrase, salt);
            
            if (!MessageDigest.isEqual(inputHash.getBytes(), storedHash.getBytes())) {
                Log.w(TAG, "Invalid passphrase");
                return false;
            }
            
            // Cache the passphrase
            cachePassphrase(passphrase);
            
            Log.i(TAG, "Database unlocked successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error unlocking database", e);
            return false;
        }
    }
    
    /**
     * Get the cached database passphrase
     * @return database key or null if not cached
     */
    public String getCachedDatabaseKey() {
        if (!isPassphraseCached()) {
            return null;
        }
        
        try {
            String saltStr = prefs.getString(KEY_SALT, null);
            if (saltStr == null) {
                return null;
            }
            
            byte[] salt = Base64.decode(saltStr, Base64.NO_WRAP);
            return deriveKey(cachedPassphrase, salt);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting cached key", e);
            return null;
        }
    }
    
    /**
     * Change the encryption passphrase
     * @param oldPassphrase Current passphrase
     * @param newPassphrase New passphrase
     * @return true if change successful
     */
    public boolean changePassphrase(char[] oldPassphrase, char[] newPassphrase) {
        // Verify old passphrase first
        if (!unlockWithPassphrase(oldPassphrase)) {
            Log.e(TAG, "Old passphrase incorrect");
            return false;
        }
        
        // Clear old data and set up new
        clearPassphrase();
        return setupEncryption(newPassphrase);
    }
    
    /**
     * Disable encryption (warning: this doesn't decrypt existing database)
     */
    public boolean disableEncryption() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_ENCRYPTION_ENABLED, false);
        editor.apply();
        
        clearPassphrase();
        
        Log.i(TAG, "Encryption disabled");
        return true;
    }
    
    /**
     * Clear cached passphrase from memory
     */
    public void clearPassphrase() {
        if (cachedPassphrase != null) {
            Arrays.fill(cachedPassphrase, '\0');
            cachedPassphrase = null;
        }
        Log.d(TAG, "Passphrase cleared from memory");
    }
    
    /**
     * Cache passphrase in memory
     */
    private void cachePassphrase(char[] passphrase) {
        if (cachedPassphrase != null) {
            Arrays.fill(cachedPassphrase, '\0');
        }
        cachedPassphrase = Arrays.copyOf(passphrase, passphrase.length);
    }
    
    /**
     * Derive database key from passphrase using PBKDF2
     */
    private String deriveKey(char[] passphrase, byte[] salt) throws Exception {
        // Simple derivation - in production, use PBKDF2WithHmacSHA256
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        
        byte[] passphraseBytes = new String(passphrase).getBytes(StandardCharsets.UTF_8);
        digest.update(passphraseBytes);
        Arrays.fill(passphraseBytes, (byte) 0); // Clear sensitive data
        
        byte[] hash = digest.digest();
        
        // Convert to hex string (64 characters for SQLCipher)
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        
        return hexString.toString();
    }
    
    /**
     * Hash passphrase for verification
     */
    private String hashPassphrase(char[] passphrase, byte[] salt) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        digest.update("verification".getBytes(StandardCharsets.UTF_8));
        
        byte[] passphraseBytes = new String(passphrase).getBytes(StandardCharsets.UTF_8);
        digest.update(passphraseBytes);
        Arrays.fill(passphraseBytes, (byte) 0);
        
        byte[] hash = digest.digest();
        return Base64.encodeToString(hash, Base64.NO_WRAP);
    }
    
    /**
     * Generate random salt
     */
    private byte[] generateSalt() {
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        return salt;
    }
    
    /**
     * Encrypt data using Android Keystore
     */
    private String encryptData(String data, byte[] salt) {
        try {
            // Get or create encryption key
            SecretKey secretKey = getOrCreateSecretKey();
            
            if (secretKey == null) {
                Log.e(TAG, "Failed to get encryption key");
                return null;
            }
            
            // Encrypt data
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            byte[] iv = cipher.getIV();
            byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            
            // Store IV
            prefs.edit().putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP)).apply();
            
            return Base64.encodeToString(encryptedData, Base64.NO_WRAP);
            
        } catch (Exception e) {
            Log.e(TAG, "Encryption error", e);
            return null;
        }
    }
    
    /**
     * Decrypt data using Android Keystore
     */
    private String decryptData(String encryptedData) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            
            SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            if (secretKey == null) {
                Log.e(TAG, "Encryption key not found");
                return null;
            }
            
            String ivStr = prefs.getString(KEY_IV, null);
            if (ivStr == null) {
                Log.e(TAG, "IV not found");
                return null;
            }
            
            byte[] iv = Base64.decode(ivStr, Base64.NO_WRAP);
            byte[] encryptedBytes = Base64.decode(encryptedData, Base64.NO_WRAP);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            
            byte[] decryptedData = cipher.doFinal(encryptedBytes);
            return new String(decryptedData, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            Log.e(TAG, "Decryption error", e);
            return null;
        }
    }
    
    /**
     * Get or create secret key in Android Keystore
     */
    private SecretKey getOrCreateSecretKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            
            // Check if key exists
            if (keyStore.containsAlias(KEY_ALIAS)) {
                return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            }
            
            // Create new key
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            
            KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build();
            
            keyGenerator.init(keySpec);
            return keyGenerator.generateKey();
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting/creating key", e);
            return null;
        }
    }
    
    /**
     * Completely reset encryption (remove all stored data)
     * WARNING: This will make encrypted databases inaccessible!
     */
    public void resetEncryption() {
        try {
            // Delete keystore key
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS);
            }
            
            // Clear preferences
            prefs.edit().clear().apply();
            
            // Clear memory
            clearPassphrase();
            
            Log.i(TAG, "Encryption reset complete");
            
        } catch (Exception e) {
            Log.e(TAG, "Error resetting encryption", e);
        }
    }
}
