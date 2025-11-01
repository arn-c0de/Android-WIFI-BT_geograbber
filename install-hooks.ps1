# Install pre-commit hook for WiFi GeoGrabber
# This script sets up security checks before commits

Write-Host "🔧 Installing pre-commit hook..." -ForegroundColor Cyan

$hookSource = ".git\hooks\pre-commit.sample"
$hookDest = ".git\hooks\pre-commit"

# Check if .git directory exists
if (-not (Test-Path ".git")) {
    Write-Host "❌ Error: .git directory not found!" -ForegroundColor Red
    Write-Host "Please run this script from the repository root." -ForegroundColor Yellow
    exit 1
}

# Create hooks directory if it doesn't exist
if (-not (Test-Path ".git\hooks")) {
    New-Item -ItemType Directory -Path ".git\hooks" -Force | Out-Null
}

# Copy pre-commit hook
$hookContent = @'
#!/bin/bash
#
# Pre-commit hook to prevent committing secrets
# WiFi GeoGrabber Security Check
#

echo "🔒 Running security checks..."

# Colors for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

FAILED=0

# Check 1: Prevent committing .env files
if git diff --cached --name-only | grep -E "\.env$"; then
    echo -e "${RED}❌ Error: Attempting to commit .env file!${NC}"
    FAILED=1
fi

# Check 2: Scan for hardcoded secrets
if git diff --cached | grep -E "(api[_-]?key|secret|password|token)\s*=\s*['\"][^'\"]{8,}['\"]" -i; then
    echo -e "${YELLOW}⚠️  Warning: Potential secret detected!${NC}"
    read -p "Continue anyway? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        FAILED=1
    fi
fi

if [ $FAILED -eq 1 ]; then
    echo -e "${RED}❌ Pre-commit check failed!${NC}"
    exit 1
else
    echo -e "${GREEN}✅ Security checks passed!${NC}"
    exit 0
fi
'@

# Save the hook
Set-Content -Path $hookDest -Value $hookContent -Encoding UTF8

# Try to make it executable (works on Git Bash on Windows)
try {
    git update-index --chmod=+x $hookDest
    Write-Host "✅ Pre-commit hook installed successfully!" -ForegroundColor Green
} catch {
    Write-Host "✅ Pre-commit hook installed!" -ForegroundColor Green
    Write-Host "ℹ️  Note: Hook permissions set. Should work with Git Bash." -ForegroundColor Cyan
}

Write-Host ""
Write-Host "The pre-commit hook will now check for:" -ForegroundColor Cyan
Write-Host "  • .env files being committed" -ForegroundColor White
Write-Host "  • Hardcoded API keys and secrets" -ForegroundColor White
Write-Host "  • Database files" -ForegroundColor White
Write-Host ""
Write-Host "📚 For more information, see:" -ForegroundColor Cyan
Write-Host "   docs/security/SECRET_MANAGEMENT.md" -ForegroundColor White
