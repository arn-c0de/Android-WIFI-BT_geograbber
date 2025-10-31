# 🚀 Contributing to GeoGrabber

Thank you for your interest in contributing to **WiFi & Bluetooth GeoGrabber**! 🎉
We welcome all types of contributions—bug fixes, new features, documentation, tests, translations, and design improvements.

---

📚 **Navigation:** [README](README.md) | [Docs](docs/README.md) | [Code of Conduct](CODE_OF_CONDUCT.md) | [License](LICENSE)

---

## 🎯 Quick Start – How You Can Help

No experience required for some tasks! You can contribute in multiple areas:

### 🧪 Testing & QA
- ✅ Add unit or integration tests (Android, Python)
- 🌐 Test across devices and platforms
- 🐞 Report bugs or performance issues

### 🌍 Documentation & Translations
- 🌐 Translate documentation (German, English, etc.)
- ✍️ Improve guides, tutorials, examples, and docstrings

### 🎨 Design & Creative
- 🎨 Refine logo or GitHub banner
- 🖌️ Design UI mockups or architecture diagrams
- 📊 Create tutorial graphics

### 💻 Development
- 🔍 Review PRs
- ♻️ Refactor code for clarity
- ✨ Add features or fix bugs ([see open issues](https://github.com/arn-c0de/Android-WIFI-BT_geograbber/issues))

### 📚 Community Support
- 💬 Answer questions on GitHub
- 📝 Share your use cases or tutorials
- 📖 Improve examples in documentation

> 💡 **Tip:** You don’t need to code—documentation, design, translations, and bug reports are equally valuable!

---

## 📜 Code of Conduct
This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating, you agree to follow its guidelines.

---

## 🤝 How to Contribute

### 🐛 Bug Reports
1. Check if the issue already exists ([Issues](https://github.com/arn-c0de/Android-WIFI-BT_geograbber/issues))
2. Open a new issue including:
   - Description, steps to reproduce, expected vs actual behavior
   - Environment: Android version, device, GeoGrabber version
   - Logs & screenshots if applicable

### 💡 Feature Requests
1. Open an issue describing the feature and use case
2. Include mockups or implementation ideas
3. Wait for maintainer feedback before starting

### 🔧 Code Contributions
1. Fork the repo
2. Create a branch: `git checkout -b feature/awesome-feature`
3. Implement changes & write tests
4. Commit using [Conventional Commits](#commit-guidelines)
5. Push to your branch & open a Pull Request

### 📖 Documentation
 - Improve `README.md`, `docs/`, docstrings, and inline comments
 - Add examples, tutorials, or translation updates

**Note:** If you change any text or documentation, please add a short entry to `CHANGELOG.md` before uploading your changes. This helps keep the history clear and ensures both files are updated together.

---

## 🛠️ Development Setup

### Prerequisites
- 🖥️ Android Studio (latest)
- 🐍 Python 3.8+
- 🔧 Git

### Quick Start

#### Android

```bash
# Clone the repository
git clone https://github.com/arn-c0de/Android-WIFI-BT_geograbber.git
cd Android-WIFI-BT_geograbber

# Open in Android Studio
# Sync Gradle and build the app
```

#### Python Tools

```bash
cd Python
python -m venv venv
venv\Scripts\activate  # Windows
source venv/bin/activate  # Linux/macOS
pip install -r requirements.txt
python start_combine_dbs.py
python start_plot_gui.py
```

---

## 🔄 Pull Request Workflow

### 🏷️ Branch Naming

* `feature/` - New features
* `fix/` - Bug fixes
* `docs/` - Documentation
* `test/` - Test improvements
* `refactor/` - Code refactoring
* `chore/` - Maintenance

### ✅ PR Checklist

* Code follows [Coding Standards](#coding-standards)
* Tests added & passing
* Documentation updated
* No secrets in code

---

## 📝 Commit Guidelines

We use **Conventional Commits**:

```
<type>(<scope>): <subject>

<body>
<footer>
```

**Types**

* `feat` - New feature
* `fix` - Bug fix
* `docs` - Documentation
* `style` - Formatting
* `refactor` - Code refactoring
* `test` - Add/modify tests
* `chore` - Maintenance
* `perf` - Performance improvement

**Example:**

```bash
git commit -m "feat(scan): add Bluetooth LE support\n\n- Implement BLE scanning\n- Add tests\nCloses #42"
```

---

## ❓ Questions?

* 💬 [GitHub Discussions](https://github.com/arn-c0de/Android-WIFI-BT_geograbber/discussions)
* 🐞 [GitHub Issues](https://github.com/arn-c0de/Android-WIFI-BT_geograbber/issues)

---

**Thank you for contributing!** Every contribution, no matter how small, helps make GeoGrabber better. 🎉
