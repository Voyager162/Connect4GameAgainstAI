# macOS Setup Guide

This guide will help you get Connect Four FX running on your Mac, whether you have Apple Silicon (M1, M2, M3) or an Intel Mac.

## Prerequisites

You need:
1. **JDK 17 or newer** (Java Development Kit)
2. **JavaFX SDK** (for local development only, not needed for running releases)

### Step 1: Install Java

#### Option A: Using Homebrew (Recommended)

```bash
# Install Homebrew if you don't have it
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install Temurin JDK 21 (recommended for Apple Silicon)
brew install temurin@21
```

#### Option B: Manual Download

Download from [Adoptium](https://adoptium.net/temurin/releases/) and choose the version matching your Mac:
- **Apple Silicon**: `aarch64` / `ARM 64`
- **Intel Mac**: `x64` / `x86_64`

Then install the `.pkg` file.

### Step 2: Verify Java Installation

```bash
java -version
javac -version
```

You should see output indicating JDK 17 or newer.

### Step 3 (Optional): Set JAVA_HOME

If Java isn't automatically in your PATH, set `JAVA_HOME`:

```bash
# Find your Java home
/usr/libexec/java_home -v 21

# Add to your shell profile (~/.zshrc for Zsh, ~/.bash_profile for Bash)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

## Running Local Builds

### Desktop Game (JavaFX GUI)

```bash
# Make the script executable (first time only)
chmod +x run-game.sh

# Run the game
./run-game.sh
```

This requires JavaFX SDK. If it fails, download JavaFX 22+ for your architecture:

1. Go to [gluonhq.com/products/javafx](https://gluonhq.com/products/javafx/)
2. Download the SDK matching your Mac:
   - **Apple Silicon**: `javafx-sdk-22-macos-aarch64.zip`
   - **Intel Mac**: `javafx-sdk-22-macos.zip`
3. Extract it and set the path:

```bash
export JAVAFX_LIB=/path/to/javafx-sdk-22/lib
./run-game.sh
```

### Terminal Game (No GUI)

```bash
chmod +x run-terminal.sh
./run-terminal.sh
```

This requires only Java, no JavaFX needed.

## Building a Release Package

### Option A: Automatic (Recommended)

The simplest way:

```bash
chmod +x package-app.sh
./package-app.sh
```

This will:
1. Auto-detect your Mac architecture (Apple Silicon or Intel)
2. Download JavaFX automatically
3. Compile and package the app
4. Create `dist/Connect Four FX.app`

### Option B: With Custom Version

```bash
export APP_VERSION="1.2.3"
./package-app.sh
```

### Option C: Manual JavaFX Setup

If you prefer to provide your own JavaFX:

```bash
export JAVAFX_LIB=/path/to/javafx-sdk-22/lib
./package-app.sh
```

## Troubleshooting

### "javac: command not found"

Set `JAVA_HOME` explicitly:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./run-game.sh
```

### "Could not find JavaFX runtime jars"

Download JavaFX SDK from [gluonhq.com](https://gluonhq.com/products/javafx/) matching your architecture, then:

```bash
export JAVAFX_LIB=/path/to/javafx-sdk-22/lib
./package-app.sh
```

### macOS security warning when opening the packaged app

The app isn't code-signed. To open it:

1. Right-click the app in Finder
2. Select "Open"
3. Click "Open" in the security dialog

The app is safe—it's pure Java with no external dependencies.

### Wrong architecture downloaded

If you get an error like "Incompatible architecture", verify your Mac type:

```bash
uname -m
# Should show: arm64 (Apple Silicon) or x86_64 (Intel)
```

The `package-app.sh` script auto-detects this, so if it fails, check that your JAVAFX_LIB points to the correct architecture.

## Support

For issues or questions, see the main [README.md](README.md) or open an issue on GitHub.
