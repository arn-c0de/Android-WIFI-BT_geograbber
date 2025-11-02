package com.example.wifi_geograbber;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * BiometricAuthManager - Handles fingerprint/face authentication for database unlock
 * 
 * Security Features:
 * - Uses Android BiometricPrompt API (supports fingerprint, face, iris)
 * - Stores encrypted passphrase in Android Keystore with biometric protection
 * - Requires biometric authentication to decrypt passphrase
 * - Falls back to manual passphrase entry if biometric unavailable
 * - Invalidates keys when biometric enrollment changes
 * 
 * Integration with EncryptionManager:
 * - Works alongside existing passphrase system
 * - User can enable/disable biometric unlock
 * - Passphrase always remains as fallback option
 */
public class BiometricAuthManager {
    private static final String TAG = "BiometricAuthManager";
    private static final String PREFS_NAME = "biometric_prefs";
    private static final String KEY_ALIAS_BIOMETRIC = "geograbber_biometric_key";
    private static final String KEY_ENCRYPTED_PASSPHRASE_BIO = "encrypted_passphrase_biometric";
    private static final String KEY_IV_BIO = "biometric_iv";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    
    private static final int GCM_TAG_LENGTH = 128;
    
    private final Context context;
    private final SharedPreferences prefs;
    private final EncryptionManager encryptionManager;
    
    /**
     * Callback for biometric authentication results
     */
    public interface BiometricAuthCallback {
        void onAuthenticationSucceeded(char[] passphrase);
        void onAuthenticationFailed();
        void onAuthenticationError(String errorMessage);
    }
    
    public BiometricAuthManager(Context context, EncryptionManager encryptionManager) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.encryptionManager = encryptionManager;
    }
    
    /**
     * Check if device supports biometric authentication
     */
    public boolean isBiometricSupported() {
        BiometricManager biometricManager = BiometricManager.from(context);
        int canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG);
        
        return canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS;
    }
    
    /**
     * Check if biometric unlock is enabled
     */
    public boolean isBiometricEnabled() {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false) && 
               prefs.contains(KEY_ENCRYPTED_PASSPHRASE_BIO);
    }
    
    /**
     * Get detailed biometric availability status
     */
    public String getBiometricStatusMessage() {
        BiometricManager biometricManager = BiometricManager.from(context);
        int canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG);
        
        switch (canAuthenticate) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                return "Biometric authentication available";
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                return "No biometric hardware available";
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                return "Biometric hardware currently unavailable";
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return "No biometric credentials enrolled";
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
                return "Security update required";
            case BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED:
                return "Biometric authentication not supported";
            case BiometricManager.BIOMETRIC_STATUS_UNKNOWN:
                return "Biometric status unknown";
            default:
                return "Unknown biometric status";
        }
    }
    
    /**
     * Enable biometric unlock by encrypting passphrase with biometric-protected key
     * @param passphrase User's current passphrase (will be encrypted and stored)
     * @return true if setup successful
     */
    public boolean enableBiometricUnlock(char[] passphrase) {
        if (!isBiometricSupported()) {
            Log.e(TAG, "Biometric authentication not supported");
            return false;
        }
        
        if (passphrase == null || passphrase.length == 0) {
            Log.e(TAG, "Invalid passphrase");
            return false;
        }
        
        try {
            // Create biometric-protected key
            SecretKey secretKey = createBiometricKey();
            
            if (secretKey == null) {
                Log.e(TAG, "Failed to create biometric key");
                return false;
            }
            
            // Encrypt passphrase with biometric key
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            byte[] iv = cipher.getIV();
            String passphraseStr = new String(passphrase);
            byte[] encryptedData = cipher.doFinal(passphraseStr.getBytes(StandardCharsets.UTF_8));
            
            // Store encrypted passphrase and IV
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_ENCRYPTED_PASSPHRASE_BIO, Base64.encodeToString(encryptedData, Base64.NO_WRAP));
            editor.putString(KEY_IV_BIO, Base64.encodeToString(iv, Base64.NO_WRAP));
            editor.putBoolean(KEY_BIOMETRIC_ENABLED, true);
            editor.apply();
            
            Log.i(TAG, "Biometric unlock enabled successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error enabling biometric unlock", e);
            return false;
        }
    }
    
    /**
     * Disable biometric unlock
     */
    public void disableBiometricUnlock() {
        try {
            // Delete biometric key from keystore
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS_BIOMETRIC)) {
                keyStore.deleteEntry(KEY_ALIAS_BIOMETRIC);
            }
            
            // Clear stored data
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(KEY_ENCRYPTED_PASSPHRASE_BIO);
            editor.remove(KEY_IV_BIO);
            editor.putBoolean(KEY_BIOMETRIC_ENABLED, false);
            editor.apply();
            
            Log.i(TAG, "Biometric unlock disabled");
            
        } catch (Exception e) {
            Log.e(TAG, "Error disabling biometric unlock", e);
        }
    }
    
    /**
     * Show biometric prompt and authenticate user
     * @param activity FragmentActivity for showing the prompt
     * @param callback Callback for authentication result
     */
    public void authenticateWithBiometric(FragmentActivity activity, BiometricAuthCallback callback) {
        if (!isBiometricEnabled()) {
            callback.onAuthenticationError("Biometric unlock not enabled");
            return;
        }
        
        try {
            // Get the biometric key and cipher
            SecretKey secretKey = getBiometricKey();
            
            if (secretKey == null) {
                Log.e(TAG, "Biometric key not found or invalidated");
                callback.onAuthenticationError("Biometric key not found. Please re-enable biometric unlock.");
                disableBiometricUnlock(); // Clean up invalid state
                return;
            }
            
            String ivStr = prefs.getString(KEY_IV_BIO, null);
            String encryptedPassphraseStr = prefs.getString(KEY_ENCRYPTED_PASSPHRASE_BIO, null);
            
            if (ivStr == null || encryptedPassphraseStr == null) {
                callback.onAuthenticationError("Biometric data corrupted");
                return;
            }
            
            byte[] iv = Base64.decode(ivStr, Base64.NO_WRAP);
            
            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            
            // Create biometric prompt
            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Database")
                .setSubtitle("Use your fingerprint to unlock encrypted database")
                .setDescription("Authentication is required to access your scan data")
                .setNegativeButtonText("Use Passphrase")
                .setConfirmationRequired(true)
                .build();
            
            // Create biometric prompt with crypto object
            BiometricPrompt biometricPrompt = new BiometricPrompt(activity,
                ContextCompat.getMainExecutor(context),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        
                        try {
                            // Decrypt passphrase using authenticated cipher
                            BiometricPrompt.CryptoObject cryptoObject = result.getCryptoObject();
                            if (cryptoObject != null && cryptoObject.getCipher() != null) {
                                Cipher authCipher = cryptoObject.getCipher();
                                byte[] encryptedBytes = Base64.decode(encryptedPassphraseStr, Base64.NO_WRAP);
                                byte[] decryptedData = authCipher.doFinal(encryptedBytes);
                                
                                String passphraseStr = new String(decryptedData, StandardCharsets.UTF_8);
                                char[] passphrase = passphraseStr.toCharArray();
                                
                                // Clear sensitive data
                                Arrays.fill(decryptedData, (byte) 0);
                                
                                Log.i(TAG, "Biometric authentication successful");
                                callback.onAuthenticationSucceeded(passphrase);
                            } else {
                                callback.onAuthenticationError("Crypto object unavailable");
                            }
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error decrypting passphrase", e);
                            callback.onAuthenticationError("Failed to decrypt passphrase");
                        }
                    }
                    
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Log.w(TAG, "Biometric authentication error: " + errString);
                        
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            // User clicked "Use Passphrase"
                            callback.onAuthenticationFailed();
                        } else {
                            callback.onAuthenticationError(errString.toString());
                        }
                    }
                    
                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Log.w(TAG, "Biometric authentication failed");
                        // Don't call callback here - user can retry
                    }
                });
            
            // Show biometric prompt with crypto object
            biometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
            
        } catch (KeyPermanentlyInvalidatedException e) {
            Log.e(TAG, "Biometric key permanently invalidated (biometric enrollment changed)", e);
            callback.onAuthenticationError("Biometric credentials have changed. Please re-enable biometric unlock.");
            disableBiometricUnlock();
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up biometric authentication", e);
            callback.onAuthenticationError("Failed to initialize biometric authentication");
        }
    }
    
    /**
     * Create a new biometric-protected key in Android Keystore
     */
    private SecretKey createBiometricKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            
            // Key requires biometric authentication for every use
            KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS_BIOMETRIC,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true) // Invalidate if biometric changes
                // Note: setUserAuthenticationValidityDurationSeconds(-1) means biometric required every time
                .build();
            
            keyGenerator.init(keySpec);
            return keyGenerator.generateKey();
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating biometric key", e);
            return null;
        }
    }
    
    /**
     * Get existing biometric key from Android Keystore
     */
    private SecretKey getBiometricKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            
            if (!keyStore.containsAlias(KEY_ALIAS_BIOMETRIC)) {
                Log.e(TAG, "Biometric key not found in keystore");
                return null;
            }
            
            return (SecretKey) keyStore.getKey(KEY_ALIAS_BIOMETRIC, null);
            
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving biometric key", e);
            return null;
        }
    }
    
    /**
     * Check if biometric key is still valid (not invalidated by enrollment changes)
     */
    public boolean isBiometricKeyValid() {
        try {
            SecretKey key = getBiometricKey();
            if (key == null) {
                return false;
            }
            
            // Try to initialize a cipher with the key
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            
            return true;
            
        } catch (KeyPermanentlyInvalidatedException e) {
            Log.w(TAG, "Biometric key permanently invalidated");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking biometric key validity", e);
            return false;
        }
    }
    
    /**
     * Update biometric enrollment (call when passphrase changes)
     * @param newPassphrase The new passphrase to encrypt with biometric protection
     */
    public boolean updateBiometricPassphrase(char[] newPassphrase) {
        if (!isBiometricEnabled()) {
            return true; // Nothing to update
        }
        
        // Disable and re-enable with new passphrase
        disableBiometricUnlock();
        return enableBiometricUnlock(newPassphrase);
    }
}
