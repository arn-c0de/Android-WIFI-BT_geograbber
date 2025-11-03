package com.example.wifi_geograbber;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;

/**
 * DatabaseUnlockActivity - Handles database unlock via biometric or passphrase
 * 
 * Features:
 * - Automatic biometric prompt if enabled
 * - Fallback to manual passphrase entry
 * - Clear UI feedback for authentication status
 * - Security: clears sensitive data from memory after use
 * 
 * Flow:
 * 1. Check if biometric unlock is available and enabled
 * 2. If yes, show biometric prompt automatically
 * 3. If biometric fails or not available, show passphrase input
 * 4. On successful unlock, proceed to MainActivity
 * 5. On failure, show error and allow retry
 */
public class DatabaseUnlockActivity extends AppCompatActivity {
    private static final String TAG = "DatabaseUnlock";
    
    private EncryptionManager encryptionManager;
    private BiometricAuthManager biometricAuthManager;
    
    private LinearLayout biometricSection;
    private LinearLayout passphraseSection;
    private ImageView fingerprintIcon;
    private TextView statusText;
    private TextView biometricStatusText;
    private EditText passphraseInput;
    private Button unlockButton;
    private Button useBiometricButton;
    private TextView attemptsText;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent screenshots and screen recording of sensitive unlock screen
        getWindow().setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        );

        setContentView(R.layout.activity_database_unlock);
        
        // Initialize managers
        encryptionManager = new EncryptionManager(this);
        biometricAuthManager = new BiometricAuthManager(this, encryptionManager);
        
        // Initialize UI
        initializeViews();
        
        // Check if encryption is enabled
        if (!encryptionManager.isEncryptionEnabled()) {
            // No encryption, proceed directly to main activity
            proceedToMainActivity();
            return;
        }
        
        // Check if passphrase is already cached
        if (encryptionManager.isPassphraseCached()) {
            // Already unlocked, proceed to main activity
            proceedToMainActivity();
            return;
        }
        
        // Check if biometric unlock is available
        if (biometricAuthManager.isBiometricEnabled() && 
            biometricAuthManager.isBiometricSupported()) {
            // Show biometric unlock UI
            showBiometricUnlock();
        } else {
            // Show passphrase unlock UI
            showPassphraseUnlock();
        }
    }
    
    private void initializeViews() {
        biometricSection = findViewById(R.id.biometric_section);
        passphraseSection = findViewById(R.id.passphrase_section);
        fingerprintIcon = findViewById(R.id.fingerprint_icon);
        statusText = findViewById(R.id.status_text);
        biometricStatusText = findViewById(R.id.biometric_status_text);
        passphraseInput = findViewById(R.id.passphrase_input);
        unlockButton = findViewById(R.id.unlock_button);
        useBiometricButton = findViewById(R.id.use_biometric_button);
        attemptsText = findViewById(R.id.attempts_text);
        
        // Set up passphrase unlock button
        unlockButton.setOnClickListener(v -> attemptPassphraseUnlock());
        
        // Set up biometric retry button
        useBiometricButton.setOnClickListener(v -> showBiometricPrompt());
        
        // Set up fallback button in biometric section
        Button usePassphraseFallbackButton = findViewById(R.id.use_passphrase_fallback_button);
        if (usePassphraseFallbackButton != null) {
            usePassphraseFallbackButton.setOnClickListener(v -> showPassphraseUnlock());
        }
        
        // Update attempts counter
        updateAttemptsDisplay();
    }
    
    private void showBiometricUnlock() {
        biometricSection.setVisibility(View.VISIBLE);
        passphraseSection.setVisibility(View.GONE);
        
        statusText.setText("Unlock Database");
        biometricStatusText.setText("Touch sensor to unlock");
        
        // Automatically show biometric prompt
        showBiometricPrompt();
    }
    
    private void showPassphraseUnlock() {
        biometricSection.setVisibility(View.GONE);
        passphraseSection.setVisibility(View.VISIBLE);
        
        statusText.setText("Enter Passphrase");
        
        // Show biometric button if available
        if (biometricAuthManager.isBiometricEnabled() && 
            biometricAuthManager.isBiometricSupported()) {
            useBiometricButton.setVisibility(View.VISIBLE);
        } else {
            useBiometricButton.setVisibility(View.GONE);
        }
    }
    
    private void showBiometricPrompt() {
        biometricAuthManager.authenticateWithBiometric(this, 
            new BiometricAuthManager.BiometricAuthCallback() {
                @Override
                public void onAuthenticationSucceeded(char[] passphrase) {
                    runOnUiThread(() -> {
                        // Cache the passphrase using EncryptionManager
                        if (encryptionManager.unlockWithPassphrase(passphrase)) {
                            encryptionManager.resetFailedAttempts();
                            Toast.makeText(DatabaseUnlockActivity.this, 
                                "Database unlocked successfully", Toast.LENGTH_SHORT).show();
                            proceedToMainActivity();
                        } else {
                            Toast.makeText(DatabaseUnlockActivity.this, 
                                "Failed to unlock database", Toast.LENGTH_SHORT).show();
                            showPassphraseUnlock();
                        }
                        
                        // Clear passphrase from memory
                        Arrays.fill(passphrase, '\0');
                    });
                }
                
                @Override
                public void onAuthenticationFailed() {
                    runOnUiThread(() -> {
                        // User cancelled or chose "Use Passphrase"
                        showPassphraseUnlock();
                    });
                }
                
                @Override
                public void onAuthenticationError(String errorMessage) {
                    runOnUiThread(() -> {
                        Toast.makeText(DatabaseUnlockActivity.this, 
                            errorMessage, Toast.LENGTH_LONG).show();
                        showPassphraseUnlock();
                    });
                }
            });
    }
    
    private void attemptPassphraseUnlock() {
        String passphraseStr = passphraseInput.getText().toString();
        
        if (passphraseStr.isEmpty()) {
            Toast.makeText(this, "Please enter passphrase", Toast.LENGTH_SHORT).show();
            return;
        }
        
        char[] passphrase = passphraseStr.toCharArray();
        
        // Check if too many failed attempts
        if (encryptionManager.shouldWipeDatabase()) {
            showDatabaseWipeDialog();
            return;
        }
        
        // Attempt to unlock
        if (encryptionManager.unlockWithPassphrase(passphrase)) {
            // Success
            encryptionManager.resetFailedAttempts();
            
            // Clear input
            passphraseInput.setText("");
            Arrays.fill(passphrase, '\0');
            
            Toast.makeText(this, "Database unlocked successfully", Toast.LENGTH_SHORT).show();
            proceedToMainActivity();
            
        } else {
            // Failed
            encryptionManager.incrementFailedAttempts();
            updateAttemptsDisplay();
            
            // Clear input
            passphraseInput.setText("");
            Arrays.fill(passphrase, '\0');
            
            int remaining = encryptionManager.getRemainingAttempts();
            if (remaining > 0) {
                Toast.makeText(this, 
                    "Incorrect passphrase. " + remaining + " attempts remaining", 
                    Toast.LENGTH_LONG).show();
            } else {
                showDatabaseWipeDialog();
            }
        }
    }
    
    private void updateAttemptsDisplay() {
        int remaining = encryptionManager.getRemainingAttempts();
        if (remaining < 5) {
            attemptsText.setVisibility(View.VISIBLE);
            attemptsText.setText("⚠️ " + remaining + " attempts remaining before database wipe");
        } else {
            attemptsText.setVisibility(View.GONE);
        }
    }
    
    private void showDatabaseWipeDialog() {
        new AlertDialog.Builder(this)
            .setTitle("⚠️ Too Many Failed Attempts")
            .setMessage("You have exceeded the maximum number of failed unlock attempts. " +
                       "For security reasons, the encrypted database must be reset.\n\n" +
                       "This will delete all scan data. Do you want to proceed?")
            .setPositiveButton("Reset Database", (dialog, which) -> {
                // Wipe database
                encryptionManager.resetEncryption();
                
                Toast.makeText(this, "Database reset. Encryption disabled.", 
                    Toast.LENGTH_LONG).show();
                
                // Proceed to main activity (will create new unencrypted database)
                proceedToMainActivity();
            })
            .setNegativeButton("Exit App", (dialog, which) -> {
                finish();
            })
            .setCancelable(false)
            .show();
    }
    
    private void proceedToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Clear sensitive data from input field
        if (passphraseInput != null) {
            passphraseInput.setText("");
        }
    }
    
    @Override
    public void onBackPressed() {
        // Prevent going back - user must unlock or exit app
        new AlertDialog.Builder(this)
            .setTitle("Exit App?")
            .setMessage("Database is locked. Do you want to exit the app?")
            .setPositiveButton("Exit", (dialog, which) -> {
                finish();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
