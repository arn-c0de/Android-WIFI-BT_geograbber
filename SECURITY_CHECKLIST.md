# 🔐 GitHub Upload Security Checklist

Quick reference guide for verifying repository security before pushing to GitHub.

---

## ⚡ Quick Commands

### 1. Check What Files Are Tracked
```bash
git ls-files
```

### 2. Verify No Sensitive Files
```bash
# Check for database files
git ls-files | grep -E "\.(db|sqlite)$"

# Check for keystores
git ls-files | grep -E "\.(jks|keystore|p12|pfx|key|pem)$"

# Check for credentials
git ls-files | grep -E "(secret|credential|api-key)"
```

### 3. Review Staged Changes
```bash
git status
git diff --cached
```

### 4. Search for Secrets in Code
```bash
# From project root
grep -r "api_key\|secret\|password\|token" WIFIGEOGRABBER/app/src/main/java/
```

### 5. Verify .gitignore is Working
```bash
# This should show the file is ignored
git check-ignore WIFIGEOGRABBER/local.properties
```

---

## ✅ Pre-Push Checklist

Before running `git push`, verify:

- [ ] Run `git status` - check for unexpected files
- [ ] No `*.db` files in staging area
- [ ] No `*.keystore` or `*.jks` files tracked
- [ ] `local.properties` is ignored
- [ ] No API keys in source code
- [ ] No hardcoded passwords or tokens
- [ ] Build artifacts (APK, AAB) are ignored
- [ ] Review `git diff` for sensitive data

---

## 🔍 What's Safe vs. What's NOT

### ✅ SAFE to Upload

```
✅ Source Code (.java files)
✅ Layout XML files
✅ Resource files (strings.xml, colors.xml)
✅ build.gradle.kts (if no secrets)
✅ AndroidManifest.xml (standard permissions)
✅ Documentation (.md files)
✅ .gitignore files
✅ Python scripts (if no credentials)
```

### ❌ NEVER Upload

```
❌ *.db (database files with user data)
❌ *.keystore, *.jks (app signing keys)
❌ local.properties (SDK paths)
❌ google-services.json (Firebase config)
❌ *.apk, *.aab (compiled apps)
❌ secrets.xml, api-keys.properties
❌ *.log (may contain sensitive debug info)
❌ credentials.json
```

---

## 🚨 Emergency: Sensitive File Already Pushed

If you accidentally pushed sensitive data:

### 1. Remove from Git History
```bash
# Using BFG Repo-Cleaner (recommended)
java -jar bfg.jar --delete-files local.properties
java -jar bfg.jar --delete-files "*.keystore"

# Or using git filter-branch
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch path/to/sensitive/file" \
  --prune-empty --tag-name-filter cat -- --all
```

### 2. Force Push (⚠️ Use with caution)
```bash
git push origin --force --all
git push origin --force --tags
```

### 3. Rotate Credentials
- Generate new API keys
- Create new keystores
- Update all services with new credentials

---

## 📋 Detailed Security Checks

### Check 1: Local Configuration
```bash
# Verify local.properties is ignored
git check-ignore -v WIFIGEOGRABBER/local.properties

# Should output:
# WIFIGEOGRABBER/.gitignore:24:local.properties
```

### Check 2: No Databases Tracked
```bash
find . -name "*.db" -type f
# Then verify none are in: git ls-files
```

### Check 3: Source Code Scan
```bash
# Scan for common secret patterns
grep -r -E "(api_key|secret|password|token|private_key)" \
  --include="*.java" \
  --include="*.xml" \
  WIFIGEOGRABBER/app/src/main/

# Should return: no matches (or only comments)
```

### Check 4: Git History Audit
```bash
# Check if sensitive files were ever committed
git log --all --full-history --source -- \
  "*.db" "*.keystore" "local.properties"

# Should return: empty (no commits)
```

---

## 🛡️ Attack Vectors & Risk Triage

Even with zero-knowledge encryption, some practical attack vectors remain. Here is a risk triage for developers:

**1. Critical**
- Weak passphrase: Highest risk, brute-force attacks possible. → Use strong, long passwords!
- Device malware: Can capture passphrase directly. → Protect your device from malware!

**2. High**
- Root/Jailbreak: Allows access to memory and bypassing app protections.
- Physical access: Unlocked device = direct data access.

**3. Medium**
- Insecure backups: Unencrypted backups may contain the database in plaintext.
- Insecure app updates: Tampered app versions could steal the passphrase.

**4. Low**
- Side-channel attacks: Very rare and technically complex.
- Timing/power analysis: Practically irrelevant for mobile apps.

**Recommendation:**
Use a strong passphrase, protect your device from malware and rooting, encrypt backups, only install trusted app updates, and do not leave your device unattended.

## 🛠️ Fix .gitignore Not Working

If files are still tracked despite being in .gitignore:

```bash
# Remove from git cache but keep file locally
git rm -r --cached .
git add .
git commit -m "Fix .gitignore"
```

---

## 📊 Current Repository Status

### Protected File Types
```
✅ *.db, *.sqlite       - Database files
✅ *.keystore, *.jks    - Signing keys
✅ *.key, *.pem         - Certificates
✅ local.properties     - SDK paths
✅ *.apk, *.aab         - Binaries
✅ *.log                - Log files
✅ build/, .gradle/     - Build artifacts
```

### Safe to Push
```
✅ Java source code
✅ XML resources
✅ Documentation
✅ Gradle configs (without secrets)
✅ Python tools
```

---

## 🔄 Regular Maintenance

### Weekly
- [ ] Review new files before committing
- [ ] Run secret scan on changed files

### Before Each Release
- [ ] Full security audit (see SECURITY_ANALYSIS.md)
- [ ] Verify no debug flags in release build
- [ ] Check ProGuard rules don't expose internals

### After Team Changes
- [ ] Rotate shared credentials
- [ ] Review access permissions
- [ ] Audit recent commits

---

## 📞 Need Help?

- **Security Issue:** See [SECURITY.md](SECURITY.md)
- **Full Analysis:** See [SECURITY_ANALYSIS.md](SECURITY_ANALYSIS.md)
- **Project Structure:** See [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)

---

## 🔐 Quick Reference Card

| If you see... | Action |
|---------------|--------|
| `*.db` in git status | ❌ DO NOT commit - add to .gitignore |
| `local.properties` modified | ✅ Normal, but should be ignored |
| `*.keystore` staged | ❌ STOP - remove immediately |
| `google-services.json` | ❌ Add to .gitignore |
| `build/` in status | ❌ Add to .gitignore |
| `*.apk` file | ❌ Add to .gitignore |

---

<div align="center">
  <p>🔒 Security is everyone's responsibility</p>
  <p><b>When in doubt, don't push it out!</b></p>
</div>
