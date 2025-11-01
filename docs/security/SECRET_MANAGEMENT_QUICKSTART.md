# 🔐 Secret Management - Quick Start

## ⚡ Quick Setup (5 minutes)

### 1. Install Dependencies

```bash
# For Python tools
pip install python-dotenv
```

### 2. Create Your `.env` File

```bash
# Copy the template
cp .env.example .env

# Edit with your values (if needed)
nano .env  # or use any text editor
```

### 3. Install Pre-commit Hook

**Windows (PowerShell):**
```powershell
.\install-hooks.ps1
```

**Linux/Mac:**
```bash
chmod +x install-hooks.sh
./install-hooks.sh
```

### 4. Verify Setup

```bash
# Check that .env is ignored
git check-ignore .env
# Should output: .env

# Test Python config
cd Python
python config.py
```

---

## 📝 Current Status

✅ **No API keys required** - This project currently works 100% offline!

The secret management system is set up for **future use** when you add:
- Google Maps API
- Cloud sync services
- External API integrations

---

## 🔍 What's Protected?

The pre-commit hook prevents committing:

- ❌ `.env` files
- ❌ `secrets.properties`
- ❌ API keys and tokens
- ❌ Database files (with confirmation)
- ❌ Credential files

---

## 📚 Documentation

- **Full Guide**: [docs/security/SECRET_MANAGEMENT.md](./docs/security/SECRET_MANAGEMENT.md)
- **Template**: [.env.example](./.env.example)
- **Config Loader**: [Python/config.py](./Python/config.py)

---

## ❓ FAQ

**Q: Do I need a `.env` file right now?**
A: No! The project works without it. It's there for future API integrations.

**Q: What if I accidentally commit a secret?**
A: See the [What to Do If You Accidentally Commit a Secret](./SECRET_MANAGEMENT.md#what-to-do-if-you-accidentally-commit-a-secret) section in the full security documentation, or visit the [Security Policy](../../SECURITY.md) for more details.


**Q: Can I skip the pre-commit hook?**
A: Yes, but not recommended. Use `git commit --no-verify` to bypass (use carefully!).

---

**Last Updated**: 2025-11-01
---


