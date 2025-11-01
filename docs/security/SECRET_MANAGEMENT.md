# Secret Management Guide for WiFi GeoGrabber

## Overview

This guide explains how to properly manage secrets, API keys, and sensitive configuration in the WiFi GeoGrabber project.

## 🔒 Security Principles

1. **Never commit secrets to version control**
2. **Use environment variables for configuration**
3. **Keep `.env` files local and private**
4. **Rotate credentials regularly**
5. **Use different credentials for development and production**

---

## 📁 File Structure

```
WiFi_GeoGrabber/
├── .env.example          # Template with placeholder values (COMMIT THIS)
├── .env                  # Actual secrets (NEVER COMMIT THIS)
├── .gitignore            # Ensures .env is not committed
├── Python/
│   ├── config.py         # Config loader for Python tools
│   └── ...
└── WIFIGEOGRABBER/
    └── app/
        └── ...
```

---

## 🚀 Setup Instructions

### For Python Tools

1. **Install python-dotenv**:
   ```bash
   pip install python-dotenv
   ```

2. **Copy the template**:
   ```bash
   cp .env.example .env
   ```

3. **Edit `.env` with your actual values**:
   ```bash
   nano .env  # or use any text editor
   ```

4. **Load environment variables in your Python scripts**:
   ```python
   from dotenv import load_dotenv
   import os
   
   # Load environment variables
   load_dotenv()
   
   # Access variables
   api_key = os.getenv('GOOGLE_MAPS_API_KEY')
   db_name = os.getenv('DB_NAME', 'wifi_scanner.db')  # with default
   ```

### For Android App

1. **Create `secrets.properties` file** (for future use):
   ```bash
   cd WIFIGEOGRABBER
   touch secrets.properties
   ```

2. **Add to `.gitignore`**:
   ```
   secrets.properties
   *.env
   .env
   .env.local
   ```

3. **Add secrets to `secrets.properties`**:
   ```properties
   GOOGLE_MAPS_API_KEY=your_actual_key_here
   FIREBASE_API_KEY=your_firebase_key_here
   ```

4. **Load in `build.gradle.kts`** (example for future use):
   ```kotlin
   android {
       defaultConfig {
           // Load from secrets.properties
           val secretsFile = rootProject.file("secrets.properties")
           if (secretsFile.exists()) {
               val secrets = Properties()
               secrets.load(FileInputStream(secretsFile))
               buildConfigField("String", "MAPS_API_KEY", 
                   "\"${secrets["GOOGLE_MAPS_API_KEY"]}\"")
           }
       }
   }
   ```

---

## 🔑 Currently Required Secrets

**As of version 1.0.2, this project does NOT require any API keys or secrets.**

The project currently:
- ✅ Uses offline OpenStreetMap tiles
- ✅ Stores data locally in SQLite
- ✅ No external API calls
- ✅ No cloud services

### Future API Integrations (Planned)

If you plan to add these features, you'll need:

| Service | Required For | Documentation |
|---------|-------------|---------------|
| Google Maps API | Custom map tiles, geocoding | [Get API Key](https://developers.google.com/maps/documentation/javascript/get-api-key) |
| Wigle.net API | WiFi database lookups | [API Documentation](https://api.wigle.net/) |
| Firebase | Cloud sync, authentication | [Firebase Console](https://console.firebase.google.com/) |
| AWS S3 | Cloud storage for databases | [AWS IAM](https://console.aws.amazon.com/iam/) |

---

## 📝 Environment Variables Reference

### Database Configuration
- `DB_NAME`: SQLite database filename (default: `wifi_scanner.db`)
- `MAX_DB_SIZE_MB`: Maximum import file size in MB (default: `100`)
- `MAX_QUERY_RECORDS`: Maximum records per query (default: `50000`)

### Security Settings
- `DEFAULT_CHECKSUM_VERIFICATION`: Enable checksum verification by default (default: `true`)
- `CHECKSUM_ALGORITHM`: Hash algorithm for checksums (default: `SHA-256`)

### Python Tools
- `DEFAULT_MAP_ZOOM`: Initial map zoom level (default: `15`)
- `MAP_TILE_PROVIDER`: Map tile source (default: `OpenStreetMap`)
- `LOG_LEVEL`: Logging verbosity (default: `INFO`)

---

## 🛡️ Security Best Practices

### 1. **Protect Your `.env` File**

```bash
# Set strict permissions (Unix/Linux/Mac)
chmod 600 .env

# Verify it's in .gitignore
git check-ignore .env
# Should output: .env
```

### 2. **Never Log Secrets**

❌ **BAD**:
```python
print(f"API Key: {api_key}")
logger.debug(f"Using key: {api_key}")
```

✅ **GOOD**:
```python
logger.debug("API Key loaded successfully")
logger.debug(f"Using key: {api_key[:8]}***")  # Only first 8 chars
```

### 3. **Use Different Credentials for Each Environment**

- **Development**: Use test/sandbox API keys
- **Production**: Use production keys with strict rate limits
- **CI/CD**: Use separate keys with minimal permissions

### 4. **Rotate Credentials Regularly**

- Change API keys every 90 days
- Revoke old keys after rotation
- Update all services immediately

### 5. **Validate Environment Variables**

```python
import os
from dotenv import load_dotenv

load_dotenv()

# Validate required variables
required_vars = ['GOOGLE_MAPS_API_KEY', 'DB_NAME']
missing_vars = [var for var in required_vars if not os.getenv(var)]

if missing_vars:
    raise ValueError(f"Missing required environment variables: {missing_vars}")
```

---

## 🔍 Checking for Leaked Secrets

### Scan Repository History

```bash
# Check for accidentally committed secrets
git log --all --full-history --source --find-copies-harder -- .env

# Search for common secret patterns
git log -p | grep -E "(api_key|secret|password|token|private_key)" -i

# Use git-secrets tool
git secrets --scan-history
```

### Use Automated Tools

```bash
# Install gitleaks
brew install gitleaks  # macOS
# or download from https://github.com/gitleaks/gitleaks

# Scan for secrets
gitleaks detect --source . --verbose

# Scan entire git history
gitleaks detect --source . --log-level debug
```

### Pre-commit Hook

Create `.git/hooks/pre-commit`:

```bash
#!/bin/bash
# Check for potential secrets before commit

if git diff --cached --name-only | grep -E "\.env$"; then
    echo "❌ Error: Attempting to commit .env file!"
    echo "Please remove .env from your commit."
    exit 1
fi

if git diff --cached | grep -E "(api_key|secret|password|token|private_key)\s*=\s*['\"]" -i; then
    echo "⚠️  Warning: Potential secret found in commit!"
    echo "Please review your changes and use environment variables."
    exit 1
fi

exit 0
```

Make it executable:
```bash
chmod +x .git/hooks/pre-commit
```

---

## 🔧 Configuration Loader (Python)

Create `Python/config.py`:

```python
import os
from dotenv import load_dotenv
from typing import Optional

class Config:
    """Configuration loader for WiFi GeoGrabber"""
    
    def __init__(self):
        # Load .env file
        load_dotenv()
        
        # Database settings
        self.DB_NAME = os.getenv('DB_NAME', 'wifi_scanner.db')
        self.MAX_DB_SIZE_MB = int(os.getenv('MAX_DB_SIZE_MB', '100'))
        self.MAX_QUERY_RECORDS = int(os.getenv('MAX_QUERY_RECORDS', '50000'))
        
        # Security settings
        self.DEFAULT_CHECKSUM_VERIFICATION = os.getenv('DEFAULT_CHECKSUM_VERIFICATION', 'true').lower() == 'true'
        self.CHECKSUM_ALGORITHM = os.getenv('CHECKSUM_ALGORITHM', 'SHA-256')
        
        # Map settings
        self.DEFAULT_MAP_ZOOM = int(os.getenv('DEFAULT_MAP_ZOOM', '15'))
        self.MAP_TILE_PROVIDER = os.getenv('MAP_TILE_PROVIDER', 'OpenStreetMap')
        
        # Logging
        self.LOG_LEVEL = os.getenv('LOG_LEVEL', 'INFO')
        self.LOG_TO_FILE = os.getenv('LOG_TO_FILE', 'false').lower() == 'true'
        self.LOG_FILE_PATH = os.getenv('LOG_FILE_PATH', './logs/geograbber.log')
        
        # Optional API keys (for future use)
        self.GOOGLE_MAPS_API_KEY = os.getenv('GOOGLE_MAPS_API_KEY')
        self.WIGLE_API_KEY = os.getenv('WIGLE_API_KEY')
        
        # Debug mode
        self.DEBUG_MODE = os.getenv('DEBUG_MODE', 'false').lower() == 'true'
    
    def validate(self):
        """Validate required configuration"""
        errors = []
        
        # Add validation rules here as needed
        # Example:
        # if self.GOOGLE_MAPS_API_KEY and len(self.GOOGLE_MAPS_API_KEY) < 10:
        #     errors.append("GOOGLE_MAPS_API_KEY appears invalid")
        
        if errors:
            raise ValueError(f"Configuration errors: {', '.join(errors)}")
        
        return True

# Global config instance
config = Config()
```

---

## 🚨 What to Do If You Accidentally Commit a Secret

1. **Immediately revoke the exposed credential**
2. **Remove from git history**:
   ```bash
   # Use BFG Repo-Cleaner
   bfg --replace-text passwords.txt  # List of secrets to remove
   git reflog expire --expire=now --all
   git gc --prune=now --aggressive
   ```

3. **Force push to all remotes**:
   ```bash
   git push origin --force --all
   git push origin --force --tags
   ```

4. **Notify your team**
5. **Generate new credentials**
6. **Update all services**

---

## 📚 Additional Resources

- [12 Factor App - Config](https://12factor.net/config)
- [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [GitHub: Removing sensitive data](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
- [python-dotenv Documentation](https://github.com/theskumar/python-dotenv)

---

## ✅ Checklist

- [ ] `.env.example` committed to repository
- [ ] `.env` added to `.gitignore`
- [ ] Local `.env` file created with actual values
- [ ] python-dotenv installed (`pip install python-dotenv`)
- [ ] Config loader implemented
- [ ] Pre-commit hook installed
- [ ] Team members notified about secret management
- [ ] Documentation updated
- [ ] Repository scanned for existing secrets
- [ ] Old secrets rotated (if any were exposed)

---

**Last Updated**: 2025-11-01
**Version**: 1.0.2
