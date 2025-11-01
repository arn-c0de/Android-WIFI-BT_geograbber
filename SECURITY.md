# Security Policy

---

📚 **Navigation:** [README](README.md) | [Contributing](CONTRIBUTING.md) | [Docs](docs/README.md) | [License](LICENSE)

---

## Security Policy

The security of WiFi & Bluetooth GeoGrabber is important to us. If you discover a vulnerability, please report it responsibly.

## Supported Versions

We provide security updates for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 1.x     | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting Vulnerabilities

### Please DO NOT report publicly

**Do NOT create public GitHub Issues for vulnerabilities.** This could put other users at risk.

### Responsible Disclosure

Please report vulnerabilities responsibly via:

#### GitHub Security Advisory (preferred)

1. Go to [Security Advisories](https://github.com/arn-c0de/Android-WIFI-BT_geograbber/security/advisories)
2. Click "Report a vulnerability"
3. Fill out the form with details


#### Email (alternative for sensitive leaks)

- **Email**: [arn-c0de@protonmail.com](mailto:arn-c0de@protonmail.com)
- **Subject**: `[SECURITY] Short Description`
- **Encryption**: Proton Mail offers end-to-end encryption

### What should the report include?

Please provide as many details as possible:

- **Type of vulnerability** (e.g. Code Injection, XSS, Arbitrary File Read)
- **Affected version(s)**
- **Steps to reproduce**
- **Proof of Concept (PoC)** code or screenshot
- **Potential impact** (e.g. RCE, data leak, DoS)
- **Suggested solution** (optional)
- **CVE-ID** (if already available)

**Example:**

```markdown
**Vulnerability:** SQL Injection in database handler

**Version:** v1.0.2

**Description:**
The function `importDatabase()` does not properly validate input, which can lead to SQL injection.

**Steps:**
1. Export a crafted .db file
2. Import it in the app
3. Malicious SQL is executed

**Impact:**
Data leak or modification

**PoC:**
Provide a crafted .db file

**Suggestion:**
Validate and sanitize all imported data
```

### Response Times

We strive for the following response times:

- **Initial response**: Within 48 hours
- **First assessment**: Within 7 days
- **Fix for critical issues**: Within 30 days
- **Fix for moderate issues**: Within 90 days

## Severity Levels

We use the [CVSS v3.1](https://www.first.org/cvss/calculator/3.1) scoring system:

| Severity    | CVSS Score | Examples                  |
|-------------|------------|---------------------------|
| **Critical**| 9.0-10.0   | RCE, Authentication Bypass|
| **High**    | 7.0-8.9    | SQL Injection, XSS        |
| **Medium**  | 4.0-6.9    | CSRF, Information Disclosure|
| **Low**     | 0.1-3.9    | Minor Information Leaks   |

## Known Security Risks

### Local Operation Required

GeoGrabber is designed for **local operation**. If exposed publicly (e.g. via exported database or Python tools):

⚠️ **Important Security Measures:**

1. **Authentication**: Protect exported data and PC analysis tools
2. **Input Validation**: Validate all imported databases
3. **Firewall**: Restrict access to sensitive data
4. **Encryption**: Use encrypted storage for sensitive data

### Data Import/Export Risks

- **Malicious .db files**: May contain harmful SQL or corrupt data
- **Data leaks**: Exported files may contain sensitive location info

**Mitigation:**
- Validate and sanitize all imported data
- Do not share exported databases publicly
- Use strong passwords for encrypted files

## Exported Data & PC Tools Mitigation

- All exported database files (.db) are fully encrypted using SQLCipher (AES-256) and require the correct passphrase to access.
- The exported sha256.json metadata file contains only the checksum, encryption salt, and file info—no sensitive data or passphrase.
- Without the passphrase, exported .db files cannot be opened, viewed, or modified, even with PC tools.
- PC tools currently do not support encrypted database files; unauthorized access is not possible unless the passphrase is known.
- CSV/JSON exports (if used) are not encrypted—avoid sharing these formats publicly if they contain sensitive data.

**Mitigation Summary:**
- Exported .db files are protected by strong encryption and passphrase authentication.
- Only share sha256.json and .db files with trusted parties and never disclose the passphrase.
- For additional protection, avoid exporting or sharing unencrypted CSV/JSON files.

### Bluetooth/WiFi Risks

- **Device spoofing**: Malicious devices may appear in scans
- **Location privacy**: Scanned data may reveal user movement

**Mitigation:**
- Only scan in authorized areas
- Do not share raw scan data without consent

### Dependency Vulnerabilities

We recommend regular dependency checks for Python tools:

```bash
pip-audit
safety check
```

## Security Features

GeoGrabber has the following built-in security features:

### 1. Input Validation
- All imported databases are checked for integrity
- User input is validated in the app and Python tools

### 2. Data Privacy
- All scan data is stored locally
- No automatic upload to external servers
- Exported files are under user control

### 3. Permissions
- Android permissions restrict access to location, WiFi, Bluetooth, and storage

### 4. Secure Config
- No secrets or API keys stored in code
- Use `.env` for Python tool secrets

## Security Best Practices

### For Users

1. **Do not commit secrets**: Use `.env` for API keys
2. **Do not share exported databases publicly**
3. **Install updates**: Keep GeoGrabber up to date
4. **Be careful with imported files**: Only use trusted sources
5. **Monitor logs**: Check app and Python tool logs regularly

### For Developers

1. **Validate input**: Check all user and file inputs
2. **Sanitize output**: Clean data before display or export
3. **Keep secrets out of code**: Never in code, always in `.env`
4. **Check dependencies**: Run `pip-audit` before every release
5. **Write tests**: Test security-relevant features

## Security Checklist Before Release

- [ ] `pip-audit` shows no critical/high vulnerabilities
- [ ] No secrets committed in code/config
- [ ] `.env.example` contains only placeholders
- [ ] Input validation for all user/file inputs
- [ ] Output sanitization for exported data
- [ ] Security tests pass
- [ ] Documentation updated

## Disclosure Policy

After fixing a vulnerability:

1. **Security advisory** is published on GitHub
2. **CVE** is requested (for high/critical)
3. **Release notes** mention the fix (without details)
4. **Credits** for the reporter (if desired)
5. **30-day waiting period** before full disclosure

## Hall of Fame

We thank the following security researchers for responsible disclosure:

<!-- 
Example format:
- **[Name]** - [Vulnerability Type] - [Month Year]
-->

*No reports yet - be the first!*

## Bug Bounty Program

Currently, we have **no official bug bounty program**.

However, we honor all security reports with:
- **Public credits** (if desired)
- **Mention in release notes**
- **Hall of Fame entry**

## Contact

- **GitHub Security**: [Security Advisories](https://github.com/arn-c0de/Android-WIFI-BT_geograbber/security/advisories)

## Further Resources

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [CWE Top 25](https://cwe.mitre.org/top25/)
- [CVSS Calculator](https://www.first.org/cvss/calculator/3.1)
- [Responsible Disclosure Policy](https://en.wikipedia.org/wiki/Responsible_disclosure)

---

**Thank you for helping keep GeoGrabber secure!** 🔒
