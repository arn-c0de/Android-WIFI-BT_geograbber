#!/bin/bash
#
# Install pre-commit hook for WiFi GeoGrabber
# This script sets up security checks before commits
#

echo "🔧 Installing pre-commit hook..."

# Check if .git directory exists
if [ ! -d ".git" ]; then
    echo "❌ Error: .git directory not found!"
    echo "Please run this script from the repository root."
    exit 1
fi

# Create hooks directory if it doesn't exist
mkdir -p .git/hooks

# Create pre-commit hook
cat > .git/hooks/pre-commit << 'EOF'
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
if git diff --cached --name-only | grep -E "\.env$|\.env\.local$"; then
    echo -e "${RED}❌ Error: Attempting to commit .env file!${NC}"
    FAILED=1
fi

# Check 2: Prevent committing secrets.properties
if git diff --cached --name-only | grep -E "secrets\.properties$"; then
    echo -e "${RED}❌ Error: Attempting to commit secrets.properties!${NC}"
    FAILED=1
fi

# Check 3: Prevent committing database files
if git diff --cached --name-only | grep -E "\.db$"; then
    echo -e "${RED}❌ Error: Attempting to commit a database (.db) file!${NC}"
    FAILED=1
fi

# Check 4: Scan for hardcoded secrets
if git diff --cached | grep -E "(api[_-]?key|secret|password|token|private[_-]?key)\s*=\s*['\"][^'\"]{8,}['\"]" -i; then
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
EOF

# Make the hook executable
chmod +x .git/hooks/pre-commit

echo "✅ Pre-commit hook installed successfully!"
echo ""
echo "The pre-commit hook will now check for:"
echo "  • .env files being committed"
echo "  • Hardcoded API keys and secrets"
echo "  • Database files"
echo ""
echo "📚 For more information, see:"
echo "   docs/security/SECRET_MANAGEMENT.md"
