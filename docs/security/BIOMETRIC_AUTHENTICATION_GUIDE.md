# 🔒 Biometric Authentication Implementation Guide

## Overview

The GeoGrabber app now supports **fingerprint and face unlock** for encrypted databases, providing a secure and convenient way to access your scan data.

## Features

✅ **Android BiometricPrompt API** - Supports fingerprint, face, and iris authentication  
✅ **Hardware-Backed Security** - Passphrase encrypted with Android Keystore biometric keys  
✅ **Zero-Knowledge Design** - Passphrase only decrypted after successful biometric authentication  
✅ **Fallback Support** - Manual passphrase entry always available  
✅ **Auto-Invalidation** - Keys invalidated when biometric enrollment changes  
✅ **Seamless Integration** - Works alongside existing encryption system

---

## Architecture

### Components

1. **BiometricAuthManager** (`BiometricAuthManager.java`)
   - Manages biometric authentication
   - Encrypts/decrypts passphrase with biometric-protected keys
   - Handles BiometricPrompt lifecycle

2. **EncryptionManager** (`EncryptionManager.java`)
   - Manages database encryption
   - Stores passphrase hash for verification
   - Caches decrypted passphrase in memory

3. **DatabaseUnlockActivity** (`DatabaseUnlockActivity.java`)
   - Entry point when database is locked
   - Shows biometric prompt or passphrase input
   - Handles unlock flow and error states

### Security Flow

```
App Launch
    │
    ├─► Check if encryption enabled
    │   └─► If NO → Open database directly
    │   └─► If YES → Continue
    │
    ├─► Check if passphrase cached
    │   └─► If YES → Open database directly
    │   └─► If NO → Show DatabaseUnlockActivity
    │
    ├─► Check if biometric enabled
    │   └─► If YES → Show biometric prompt
    │   │   ├─► Success → Decrypt passphrase → Unlock database
    │   │   └─► Fail → Show passphrase input
    │   └─► If NO → Show passphrase input directly
    │
    └─► Passphrase Input
        ├─► Verify passphrase
        ├─► Cache in memory
        └─► Open database
```

---

## Implementation Details

### 1. Biometric Key Generation

```java
KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
    KEY_ALIAS_BIOMETRIC,
    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .setUserAuthenticationRequired(true)  // ← Requires biometric auth
    .setInvalidatedByBiometricEnrollment(true)  // ← Auto-invalidates on changes
    .build();
```

**Key Properties:**
- **AES-256-GCM** encryption for authenticated encryption
- **User authentication required** - Key can only be used after biometric auth
- **Invalidated on enrollment changes** - Adds or removes fingerprints = key deleted
- **Stored in Android Keystore** - Hardware-backed, cannot be extracted

### 2. Passphrase Encryption

When user enables biometric unlock:

1. User enters current passphrase
2. Passphrase is verified via `EncryptionManager.unlockWithPassphrase()`
3. Biometric key is created in Android Keystore
4. Passphrase is encrypted with biometric key using AES-256-GCM
5. Encrypted passphrase + IV stored in SharedPreferences
6. Original passphrase cleared from memory

**Storage:**
```
SharedPreferences (biometric_prefs):
├─ encrypted_passphrase_biometric: [Base64 encrypted passphrase]
├─ biometric_iv: [Base64 initialization vector]
└─ biometric_enabled: true
```

### 3. Biometric Authentication

When user launches app with biometric enabled:

1. `DatabaseUnlockActivity` shows biometric prompt
2. User authenticates with fingerprint/face
3. On success, `BiometricPrompt.CryptoObject` provides authenticated cipher
4. Cipher decrypts the stored passphrase
5. Decrypted passphrase passed to `EncryptionManager.unlockWithPassphrase()`
6. Database unlocked, passphrase cached in memory
7. User proceeds to `MainActivity`

**BiometricPrompt Configuration:**
```java
BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
    .setTitle("Unlock Database")
    .setSubtitle("Use your fingerprint to unlock encrypted database")
    .setNegativeButtonText("Use Passphrase")  // Fallback option
    .setConfirmationRequired(true)  // Requires explicit confirmation
    .build();
```

### 4. Security Considerations

#### ✅ **Secure Storage**
- Passphrase **never** stored in plaintext
- Encrypted with hardware-backed biometric key
- Key cannot be extracted from Android Keystore

#### ✅ **Zero-Knowledge**
- Passphrase only decrypted after successful biometric auth
- Biometric authentication happens at OS level (secure)
- App never sees raw biometric data

#### ✅ **Auto-Invalidation**
- If user adds/removes fingerprints, biometric key is invalidated
- User must re-enable biometric unlock with passphrase
- Prevents unauthorized access after biometric enrollment changes

#### ✅ **Fallback Support**
- Manual passphrase entry always available
- Failed attempts counter enforced
- Database wipe after 5 failed passphrase attempts

#### ✅ **Memory Protection**
- Passphrase stored as `char[]` not `String` (can be cleared)
- Cleared from memory after use with `Arrays.fill(passphrase, '\0')`
- Security overlay hides content during passphrase entry

---

## Usage Guide

### For Users

#### **Enable Biometric Unlock**

1. Open app and tap **More** button
2. Select **Encryption Settings**
3. Tap **🔒 Enable Biometric Unlock**
4. Enter your current passphrase to verify
5. Touch fingerprint sensor when prompted
6. Done! Next time you open the app, use fingerprint to unlock

#### **Disable Biometric Unlock**

1. Open app and tap **More** button
2. Select **Encryption Settings**
3. Tap **🔓 Disable Biometric Unlock**
4. Confirm to disable

#### **Unlock with Biometric**

1. Launch app
2. Touch fingerprint sensor when prompted
3. On success, database unlocks automatically

#### **Fallback to Passphrase**

1. Launch app
2. Tap **Use Passphrase Instead** on biometric prompt
3. Enter your passphrase manually
4. Tap **Unlock**

---

### For Developers

#### **Check Biometric Support**

```java
BiometricAuthManager bioManager = new BiometricAuthManager(context, encryptionManager);

if (bioManager.isBiometricSupported()) {
    // Device has biometric hardware and enrolled biometrics
    String status = bioManager.getBiometricStatusMessage();
    // "Biometric authentication available"
}
```

#### **Enable Biometric Unlock**

```java
char[] passphrase = getUserPassphrase();  // Get from user input

if (bioManager.enableBiometricUnlock(passphrase)) {
    // Successfully enabled
    Log.i(TAG, "Biometric unlock enabled");
} else {
    // Failed
    Log.e(TAG, "Failed to enable biometric unlock");
}

// Always clear passphrase from memory
Arrays.fill(passphrase, '\0');
```

#### **Authenticate with Biometric**

```java
bioManager.authenticateWithBiometric(activity, new BiometricAuthManager.BiometricAuthCallback() {
    @Override
    public void onAuthenticationSucceeded(char[] passphrase) {
        // Biometric auth successful, got decrypted passphrase
        if (encryptionManager.unlockWithPassphrase(passphrase)) {
            // Database unlocked
            proceedToMainActivity();
        }
        Arrays.fill(passphrase, '\0');  // Clear from memory
    }
    
    @Override
    public void onAuthenticationFailed() {
        // User cancelled or chose "Use Passphrase"
        showPassphraseInput();
    }
    
    @Override
    public void onAuthenticationError(String errorMessage) {
        // Error occurred (key invalidated, hardware unavailable, etc.)
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show();
        showPassphraseInput();
    }
});
```

#### **Disable Biometric Unlock**

```java
bioManager.disableBiometricUnlock();
// Biometric key deleted, encrypted passphrase removed
```

---

## API Reference

### BiometricAuthManager

#### Constructor
```java
BiometricAuthManager(Context context, EncryptionManager encryptionManager)
```

#### Methods

| Method | Description | Returns |
|--------|-------------|---------|
| `isBiometricSupported()` | Check if device supports biometric auth | `boolean` |
| `isBiometricEnabled()` | Check if biometric unlock is enabled | `boolean` |
| `getBiometricStatusMessage()` | Get detailed biometric availability status | `String` |
| `enableBiometricUnlock(char[] passphrase)` | Enable biometric unlock with passphrase | `boolean` |
| `disableBiometricUnlock()` | Disable biometric unlock | `void` |
| `authenticateWithBiometric(activity, callback)` | Show biometric prompt and authenticate | `void` |
| `isBiometricKeyValid()` | Check if biometric key is still valid | `boolean` |
| `updateBiometricPassphrase(char[] newPassphrase)` | Update passphrase when changed | `boolean` |

#### Callback Interface

```java
public interface BiometricAuthCallback {
    void onAuthenticationSucceeded(char[] passphrase);
    void onAuthenticationFailed();
    void onAuthenticationError(String errorMessage);
}
```

---

## Testing

### Test Cases

#### ✅ **Enable Biometric Unlock**
1. Enable encryption with passphrase
2. Go to Encryption Settings
3. Tap "Enable Biometric Unlock"
4. Enter correct passphrase
5. Verify: Biometric unlock enabled

#### ✅ **Unlock with Biometric**
1. Close and reopen app
2. Biometric prompt appears automatically
3. Touch fingerprint sensor
4. Verify: Database unlocked, main screen shown

#### ✅ **Fallback to Passphrase**
1. Close and reopen app
2. Tap "Use Passphrase Instead"
3. Enter passphrase
4. Verify: Database unlocked

#### ✅ **Failed Biometric Auth**
1. Close and reopen app
2. Use wrong finger 3 times
3. Verify: Biometric prompt fails, passphrase input shown

#### ✅ **Biometric Enrollment Change**
1. Enable biometric unlock
2. Go to Android Settings → Security → Fingerprint
3. Add or remove a fingerprint
4. Close and reopen app
5. Verify: Error message "Biometric credentials have changed"
6. Re-enable biometric unlock with passphrase

#### ✅ **Disable Biometric Unlock**
1. Go to Encryption Settings
2. Tap "Disable Biometric Unlock"
3. Close and reopen app
4. Verify: Passphrase input shown (no biometric prompt)

---

## Troubleshooting

### "No biometric hardware available"
**Cause:** Device doesn't have fingerprint sensor or face unlock  
**Solution:** Use passphrase-only unlock

### "No biometric credentials enrolled"
**Cause:** User hasn't set up fingerprints/face in Android settings  
**Solution:** Go to Settings → Security → Fingerprint/Face and enroll biometrics

### "Biometric key permanently invalidated"
**Cause:** User added or removed fingerprints after enabling biometric unlock  
**Solution:** Disable and re-enable biometric unlock with passphrase

### "Biometric authentication not supported"
**Cause:** Device running Android < 6.0 or security update required  
**Solution:** Update Android OS or use passphrase-only unlock

---

## Future Enhancements

### Potential Improvements

1. **Biometric Timeout** - Auto-lock after X minutes of inactivity
2. **Multiple Biometric Methods** - Support fingerprint + face simultaneously
3. **Biometric-Protected Actions** - Require biometric for sensitive operations (export DB, etc.)
4. **Smart Lock Integration** - Keep unlocked when connected to trusted devices
5. **Pattern/PIN Backup** - Additional unlock methods besides passphrase

---

## Dependencies

### Required Libraries

```gradle
dependencies {
    // Biometric authentication
    implementation("androidx.biometric:biometric:1.1.0")
    
    // Existing dependencies
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.4.0")
}
```

### Required Permissions

```xml
<!-- Biometric authentication -->
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
<uses-feature android:name="android.hardware.fingerprint" android:required="false"/>
```

---

## Security Analysis

### Threat Model

| Threat | Mitigation |
|--------|------------|
| **Unauthorized access** | Biometric auth required to decrypt passphrase |
| **Passphrase extraction** | Stored encrypted, only decrypted after biometric auth |
| **Biometric spoofing** | Android OS handles biometric security (secure element) |
| **Key extraction** | Android Keystore hardware-backed, keys cannot be extracted |
| **Biometric changes** | Keys auto-invalidated, user must re-enroll |
| **Memory dumps** | Passphrase cleared from memory after use |
| **Failed attempts** | Counter enforced, database wiped after 5 failures |

### Security Best Practices Applied

✅ **Defense in Depth** - Multiple layers (biometric + passphrase + keystore)  
✅ **Least Privilege** - Biometric key only for passphrase decryption  
✅ **Fail Secure** - Defaults to passphrase on any biometric error  
✅ **Audit Logging** - All unlock attempts logged  
✅ **Secure Defaults** - Biometric disabled by default, user must opt-in  

---

## Changelog

### v1.0.3
- ✅ Initial biometric authentication implementation
- ✅ BiometricAuthManager class created
- ✅ DatabaseUnlockActivity with biometric UI
- ✅ Integration with existing EncryptionManager
- ✅ Settings UI for enable/disable biometric unlock
- ✅ Comprehensive security testing

---

## References

- [Android Biometric API Guide](https://developer.android.com/training/sign-in/biometric-auth)
- [Android Keystore System](https://developer.android.com/training/articles/keystore)
- [BiometricPrompt API Reference](https://developer.android.com/reference/androidx/biometric/BiometricPrompt)
- [SQLCipher Documentation](https://www.zetetic.net/sqlcipher/documentation/)

---

# 🧬 Biometric Authentication Guide

## First Setup & Security Information

**Sicherheit beim Biometric Unlock:**
- Dein Passkey wird niemals im Klartext gespeichert.
- Er wird mit dem Android Keystore verschlüsselt (AES-256, hardware-gesichert).
- Nur nach erfolgreicher Biometrie (Fingerabdruck/Gesicht) kann der Passkey entschlüsselt werden.
- Biometrische Daten verlassen das Gerät nie und werden nicht in der App gespeichert.
- Nach der Entschlüsselung wird der Passkey sofort aus dem Speicher gelöscht.

**Was ist sicherer?**
- Ein langer, starker Passkey, den du nur manuell eingibst und nie speicherst, ist maximal sicher.
- Biometric Unlock ist sehr sicher und komfortabel, aber der Passkey wird einmalig verschlüsselt im Keystore abgelegt.
- Für die meisten Nutzer ist Biometric Unlock mit starkem Passkey ein sehr guter Kompromiss aus Sicherheit und Komfort.

## Schritt-für-Schritt Einrichtung

1. **Öffne die App** und gehe zu den **Einstellungen**.
2. Tippe auf **Biometric Unlock aktivieren**.
3. **Bestätige deinen aktuellen Passphrase**, wenn du dazu aufgefordert wirst.
4. **Berühre den Fingerabdrucksensor** oder schaue auf die Kamera für die Gesichtserkennung.
5. Bei erfolgreicher Authentifizierung siehst du eine Bestätigung.
6. **Fertig!** Ab sofort kannst du die biometrische Authentifizierung zum Entsperren der App verwenden.

## Nutzung der Biometric Authentication

- Bei jedem Start der App wirst du aufgefordert, deine Identität mit deinem Fingerabdruck oder Gesicht zu bestätigen.
- Wenn die biometrische Authentifizierung fehlschlägt, kannst du alternativ deine Passphrase eingeben.
- Deine biometrischen Daten werden niemals gespeichert oder an Dritte weitergegeben.

---
