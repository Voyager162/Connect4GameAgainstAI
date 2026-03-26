# Connect Four FX

`Connect Four FX` is a desktop Connect Four game written in Java, with a JavaFX interface for the main game and a terminal version for quick testing.

## What Is In This Repo

- `ConnectFourFX.java`: the desktop game with animations, polished UI, turn status, and a live position outlook meter
- `TerminalConnectFour.java`: a console version that uses the same rules and AI ideas
- `connect-four.css`: styling for the desktop app
- `run-game.bat`: compile and run the JavaFX desktop app locally
- `run-terminal.bat`: compile and run the terminal version locally
- `package-app.ps1`: cross-platform packaging script used for local builds and GitHub Actions
- `package-app.bat`: Windows wrapper for the packaging script
- `.github/workflows/release.yml`: GitHub Actions workflow that builds downloadable Windows and macOS releases from tags

## Gameplay Notes

- Standard 7-column by 6-row Connect Four board
- Human player uses `X`; the AI uses `O`
- Starting player is chosen randomly each round
- The AI searches moves with minimax plus alpha-beta pruning
- Move ordering prefers the center columns first
- Search depth increases as the board fills up
- The desktop app highlights the winning line and shows a live "Position Outlook" evaluation

## Local Setup

### Prerequisites

- Windows or macOS
- JDK 17 or newer
- JavaFX runtime jars for your platform

You can point the scripts at JavaFX in either of these ways:

1. Set `JAVAFX_LIB` to a folder containing either:
   - `javafx.base.jar`, `javafx.graphics.jar`, `javafx.controls.jar`
   - or `javafx-base-<version>-<platform>.jar`, `javafx-graphics-<version>-<platform>.jar`, `javafx-controls-<version>-<platform>.jar`
2. Or place those jars inside `javafx-sdk\lib` in the repo root

Example PowerShell session:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot"
$env:JAVAFX_LIB = "C:\path\to\javafx\lib"
```

## Run The Game Locally

Desktop app:

```powershell
.\run-game.bat
```

Terminal app:

```powershell
.\run-terminal.bat
```

## Build A Downloadable Release

Run:

```powershell
.\package-app.ps1
```

That creates:

- on Windows: `dist\Connect Four FX\` plus a zip like `Connect Four FX-<version>-portable.zip`
- on macOS: `dist/Connect Four FX.app` plus a zip like `Connect Four FX-<version>-macos-<arch>.zip`

You can override the app version before running the packaging script:

```powershell
$env:APP_VERSION = "1.0.0"
.\package-app.ps1
```

Tags like `v1.0.0` are also fine; the packaging script will normalize that to the numeric version format required by `jpackage`.

## GitHub Release Automation

This repo includes a GitHub Actions workflow that:

- runs on Windows and macOS
- installs Java
- downloads the required JavaFX jars
- builds release zips for Windows x64, macOS Intel, and macOS Apple Silicon
- uploads the build artifacts
- automatically attaches the zips to a GitHub Release when you push a tag like `v1.0.0`

## macOS Note

The macOS app can be built and downloaded from GitHub Releases, but it is not code signed or notarized yet. That means macOS may show an "unidentified developer" warning the first time someone opens it.

## Recommended Publishing Checklist

1. Add a license before making the repository public
2. Commit the source files, scripts, workflow, and README
3. Do not commit `build/`, `dist/`, `.class` files, or local JavaFX jars
4. Push to GitHub
5. Create and push a version tag such as `v1.0.0`
6. Wait for the GitHub Actions workflow to finish
7. Open the GitHub Release page and confirm the zip asset is attached
