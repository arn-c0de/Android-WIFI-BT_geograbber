# Security Policy

## Supported Versions

| Version | Supported |
| ------- | --------- |
| 1.0.6   | yes       |
| <= 1.0  | no        |

## Reporting Vulnerabilities

**Do not open public issues for security vulnerabilities.**

Report via [GitHub Security Advisory](https://github.com/arn-c0de/Geograbber/security/advisories) (preferred) or email [arn-c0de@protonmail.com](mailto:arn-c0de@protonmail.com).

Include: vulnerability type, affected version, steps to reproduce, impact, and optional PoC.

**Response times:** initial response within 48 h, fix for critical issues within 30 days.

## Severity (CVSS v3.1)

| Severity | Score    |
|----------|----------|
| Critical | 9.0–10.0 |
| High     | 7.0–8.9  |
| Medium   | 4.0–6.9  |
| Low      | 0.1–3.9  |

## Security Features

- AES-256 database encryption via SQLCipher
- PBKDF2-HMAC-SHA256 with 600,000 iterations
- Android Keystore for key storage
- Biometric authentication support
- SHA-256 checksum verification for import/export
- No data transmitted to external servers

## Known Risks

- Exported CSV/JSON files are unencrypted — do not share publicly
- Scanned data may contain location information — only scan in authorized areas
- Only import databases from trusted sources

## Disclosure

After a fix is released: security advisory published, CVE requested for high/critical, reporter credited if desired, 30-day waiting period before full disclosure.
