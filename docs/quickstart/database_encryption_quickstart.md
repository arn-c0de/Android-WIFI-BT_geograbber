# 🔐 Database Encryption - User Guide

## What is Database Encryption?

Database encryption protects your WiFi and Bluetooth scan data by scrambling it with a password (passphrase). Even if someone gains access to your device or database file, they cannot read the data without knowing your passphrase.

---

## 🎯 Why Use Encryption?

### Privacy Protection
- **GPS Locations:** Your movement patterns stay private
- **Network Data:** WiFi SSIDs, MAC addresses, signal strengths protected
- **Bluetooth Devices:** Paired devices and their locations secured

### Security Benefits
- ✅ Prevents unauthorized access to scan history
- ✅ Protects against device theft or loss
- ✅ Secure for enterprise/sensitive environments
- ✅ Meets privacy best practices

---

## 🚀 Quick Start

### First-Time Setup

1. **Launch the app** (first time or after update)

2. **Encryption prompt appears:**
   ```
   ┌────────────────────────────────────┐
   │ Would you like to enable database  │
   │ encryption to protect your data?   │
   │                                    │
   │ 🔐 Recommended for privacy         │
   │                                    │
   │ [Enable Now]  [Maybe Later]        │
   └────────────────────────────────────┘
   ```

3. **Choose "Enable Now"**

4. **Set your passphrase:**
   ```
   ┌────────────────────────────────────┐
   │ Create Passphrase                  │
   │                                    │
   │ Enter passphrase:                  │
   │ [________________]                 │
   │                                    │
   │ Confirm passphrase:                │
   │ [________________]                 │
   │                                    │
   │ ☐ Show passphrase                  │
   │                                    │
   │ [Set Passphrase]  [Cancel]         │
   └────────────────────────────────────┘
   ```

5. **Wait for migration:**
   ```
   Encrypting database...
   ⏳ Please wait...
   ```

6. **Done!**
   ```
   ✓ Database encrypted successfully!
   ```

---

## 🔓 Unlocking Your Database

Every time you launch the app with encryption enabled, you'll need to unlock it:

```
┌────────────────────────────────────┐
│ Unlock Database                    │
│                                    │
│ Enter your passphrase:             │
│ [________________]                 │
│                                    │
│ [Unlock]  [Cancel]                 │
└────────────────────────────────────┘
```

**Important:** The app stays unlocked during your session but **locks automatically** when you close it or switch apps.

---

## ⚙️ Managing Encryption

### Access Encryption Settings

1. Open the app
2. Tap **"More"** button
3. Select **"🔐 Database Encryption"**

### Available Options

#### When Encryption is Enabled:
```
┌────────────────────────────────────┐
│ Database Encryption                │
│                                    │
│ Status: 🔒 Encrypted               │
│                                    │
│ Options:                           │
│ • Change Passphrase               │
│ • Disable Encryption              │
│                                    │
│ [Cancel]                           │
└────────────────────────────────────┘
```

#### When Encryption is Disabled:
```
┌────────────────────────────────────┐
│ Database Encryption                │
│                                    │
│ Status: 🔓 Not Encrypted           │
│                                    │
│ Options:                           │
│ • Enable Encryption               │
│                                    │
│ [Cancel]                           │
└────────────────────────────────────┘
```

---

## 🔄 Changing Your Passphrase

1. **Go to:** More → 🔐 Database Encryption → Change Passphrase

2. **Enter passphrases:**
   ```
   ┌────────────────────────────────────┐
   │ Change Passphrase                  │
   │                                    │
   │ Current passphrase:                │
   │ [________________]                 │
   │                                    │
   │ New passphrase:                    │
   │ [________________]                 │
   │                                    │
   │ Confirm new passphrase:            │
   │ [________________]                 │
   │                                    │
   │ [Change]  [Cancel]                 │
   └────────────────────────────────────┘
   ```

3. **Wait for re-encryption:**
   ```
   Re-encrypting database...
   ⏳ Please wait...
   ```

4. **Success:**
   ```
   ✓ Passphrase changed successfully
   ```

**Important:** Make sure to remember your new passphrase! There is no recovery option.

---

## 🔓 Disabling Encryption

⚠️ **WARNING:** This will remove all protection from your database!

1. **Go to:** More → 🔐 Database Encryption → Disable Encryption

2. **Read the warning:**
   ```
   ┌────────────────────────────────────┐
   │ ⚠️ WARNING                         │
   │                                    │
   │ This will decrypt your database!   │
   │                                    │
   │ • Data will be stored in plaintext │
   │ • Anyone can read it              │
   │ • Privacy protection removed      │
   │                                    │
   │ Are you sure?                      │
   │                                    │
   │ [Yes, Disable]  [Cancel]           │
   └────────────────────────────────────┘
   ```

3. **Confirm if you're sure**

4. **Database decrypted:**
   ```
   ✓ Database decrypted
   Encryption disabled
   ```

---

## 📤 Exporting Encrypted Databases

When you export your database with encryption enabled:

1. **Go to:** More → Save DB as file

2. **Database exported as encrypted file:**
   ```
   ✓ Database exported!
   
   File: wifiscannerexport_123456.db
   
   Note: This file is encrypted.
   You'll need your passphrase to import it.
   ```

3. **Share safely:** The exported .db file is protected with your passphrase

---

## 📥 Importing Encrypted Databases

1. **Go to:** More → Import external DB

2. **Select encrypted .db file**

3. **Encryption detected:**
   ```
   ┌────────────────────────────────────┐
   │ 🔒 Encrypted Database Detected     │
   │                                    │
   │ Enter passphrase to import:        │
   │ [________________]                 │
   │                                    │
   │ [Import]  [Cancel]                 │
   └────────────────────────────────────┘
   ```

4. **Enter correct passphrase**

5. **Database imported:**
   ```
   ✓ Encrypted database imported!
   ```

---

## 💡 Tips & Best Practices

### Passphrase Guidelines
✅ **DO:**
- Use at least 12 characters (longer = stronger)
- Mix letters, numbers, and symbols
- Make it memorable but not obvious
- Consider using a passphrase (e.g., "WiFi@Home2025!")

❌ **DON'T:**
- Use dictionary words
- Use personal info (birthdate, name, etc.)
- Use simple patterns (123456, password, etc.)
- Share with anyone

### Security Tips
- 🔒 **Lock your device** with screen lock/biometrics
- 💾 **Backup regularly** (encrypted exports)
- 🔄 **Change passphrase** periodically (every 3-6 months)
- 🚫 **Never store** passphrase in notes or messages
- ⚠️ **Be careful** when importing unknown databases

---

## ❓ Frequently Asked Questions

### Q: What if I forget my passphrase?
**A:** Unfortunately, there is no recovery option. This is by design to ensure maximum security. Your data will be inaccessible. Always keep a secure backup of your passphrase.

### Q: Can someone break the encryption?
**A:** We use AES-256 encryption (military-grade). With a strong passphrase, it would take billions of years to crack with current technology.

### Q: Does encryption slow down the app?
**A:** Minimal impact. You might notice ~200ms slower startup, but day-to-day scanning and viewing is barely affected (<5% slower).

### Q: Can I use this on multiple devices?
**A:** Yes! Export your encrypted database and import it on another device with the same passphrase.

### Q: What happens if I lose my device?
**A:** Your data is protected. Even if someone extracts the database file, they cannot read it without your passphrase.

### Q: Is this really secure?
**A:** Yes. We use:
- ✅ SQLCipher (industry-standard database encryption)
- ✅ AES-256 encryption (government-grade)
- ✅ Android Keystore (hardware-backed security)
- ✅ No cloud storage (data stays on your device)

### Q: Can I switch between encrypted and unencrypted?
**A:** Yes. You can enable/disable encryption at any time through the settings. However, disabling removes all protection.

### Q: Does this protect against malware?
**A:** Partial protection. Encryption protects data at rest (stored on disk). If malware runs with app permissions, it could potentially access unlocked data in memory.

---

## 🆘 Troubleshooting

### Problem: "Wrong passphrase" error
**Solution:** 
- Make sure Caps Lock is off
- Try typing carefully
- If you genuinely forgot, there's no recovery (by design)

### Problem: Migration failed
**Solution:**
- Check available storage space
- Ensure database isn't corrupted
- Try restarting the app
- Contact support if issue persists

### Problem: App crashes after enabling encryption
**Solution:**
- Clear app cache (Settings → Apps → GeoGrabber → Clear Cache)
- Reinstall app (your database will be preserved)
- Report bug with logs

### Problem: Can't unlock database
**Solution:**
- Force close app and reopen
- Restart device
- If passphrase is definitely correct, database may be corrupted

---

## 📞 Support

If you encounter issues:

1. **Check this guide** for solutions
2. **Review app logs** (More → Debug Log)
3. **Create an issue** on GitHub with:
   - Device model and Android version
   - Steps to reproduce
   - Error messages/screenshots

---

## 🔐 Privacy & Compliance

### What We DON'T Do:
- ❌ No cloud storage of data
- ❌ No telemetry or tracking
- ❌ No backdoor access
- ❌ No passphrase recovery (we can't access your data)

### What You Control:
- ✅ Your passphrase (only you know it)
- ✅ Your data (stays on your device)
- ✅ Your exports (you control who gets them)
- ✅ Your privacy (encryption is optional)

---

**Remember:** Encryption is powerful but only as strong as your passphrase. Choose wisely! 🔐

---

*Need more help? Check the full documentation at:*
*`docs/security/DATABASE_ENCRYPTION_GUIDE.md`*
