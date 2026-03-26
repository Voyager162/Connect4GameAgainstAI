#!/bin/bash
set -e

# macOS/Linux packaging script for Connect Four FX
# This is a Bash equivalent of the PowerShell script for easier usage on Unix-like systems

APP_VERSION="${APP_VERSION:-}"
JAVAFX_LIB="${JAVAFX_LIB:-}"
JAVAFX_CLASSIFIER="${JAVAFX_CLASSIFIER:-}"
JAVA_HOME="${JAVA_HOME:-}"

function get_platform_info() {
    local classifier="$1"
    
    case "$classifier" in
        mac)
            echo "macos|Connect Four FX.app|macos-x64|\$APPDIR/javafx"
            ;;
        mac-aarch64)
            echo "macos|Connect Four FX.app|macos-arm64|\$APPDIR/javafx"
            ;;
        linux)
            echo "linux|Connect Four FX|linux-x64|\$APPDIR/javafx"
            ;;
        *)
            echo "Error: Unsupported JAVAFX_CLASSIFIER '$classifier'. Use mac, mac-aarch64, or linux." >&2
            exit 1
            ;;
    esac
}

function normalize_version() {
    local raw_version="$1"
    
    if [ -z "$raw_version" ]; then
        raw_version="1.0.0"
    fi
    
    # Extract numeric parts from version string
    echo "$raw_version" | grep -oE '[0-9]+' | head -3 | paste -sd '.' -
}

function resolve_java_command() {
    local java_home="$1"
    local command="$2"
    
    if [ -n "$java_home" ] && [ -f "$java_home/bin/$command" ]; then
        echo "$java_home/bin/$command"
        return 0
    fi
    
    if command -v "$command" &> /dev/null; then
        command -v "$command"
        return 0
    fi
    
    echo "Error: Could not find '$command'. Set JAVA_HOME to a JDK 17+ install and try again." >&2
    exit 1
}

function resolve_javafx_jar() {
    local search_dir="$1"
    local base_name="$2"
    local classifier="$3"
    
    if [ -z "$search_dir" ] || [ ! -d "$search_dir" ]; then
        return 1
    fi
    
    # Try direct JAR names first
    if [ -f "$search_dir/$base_name.jar" ]; then
        echo "$search_dir/$base_name.jar"
        return 0
    fi
    
    # Try with version pattern
    local pattern="${base_name}-*-${classifier}.jar"
    local found=$(find "$search_dir" -maxdepth 1 -name "$pattern" -type f | sort | head -1)
    
    if [ -n "$found" ]; then
        echo "$found"
        return 0
    fi
    
    return 1
}

function get_javafx_jars() {
    local search_dir="$1"
    local classifier="$2"
    
    local base=$(resolve_javafx_jar "$search_dir" "javafx.base" "$classifier")
    local graphics=$(resolve_javafx_jar "$search_dir" "javafx.graphics" "$classifier")
    local controls=$(resolve_javafx_jar "$search_dir" "javafx.controls" "$classifier")
    
    if [ -n "$base" ] && [ -n "$graphics" ] && [ -n "$controls" ]; then
        echo "$base:$graphics:$controls"
        return 0
    fi
    
    return 1
}

function rewrite_launcher_config() {
    local config_path="$1"
    local javafx_path_value="$2"
    
    if [ ! -f "$config_path" ]; then
        echo "Error: Could not find launcher config at '$config_path'." >&2
        exit 1
    fi
    
    # Create a backup
    cp "$config_path" "${config_path}.bak"
    
    # Remove old JavaFX references and add new ones
    grep -v "app.classpath=.*javafx" "$config_path" | \
        grep -v "java-options=--module-path=" | \
        grep -v "java-options=--add-modules=javafx.controls" > "${config_path}.tmp"
    
    # Find [JavaOptions] section and insert after it
    local line_num=$(grep -n "\[JavaOptions\]" "${config_path}.tmp" | cut -d: -f1)
    
    if [ -z "$line_num" ]; then
        echo "Error: Launcher config is missing a [JavaOptions] section." >&2
        rm "${config_path}.tmp" "${config_path}.bak"
        exit 1
    fi
    
    local head_lines=$((line_num))
    local tail_lines=$(wc -l < "${config_path}.tmp")
    tail_lines=$((tail_lines - head_lines))
    
    head -n "$head_lines" "${config_path}.tmp" > "$config_path"
    echo "java-options=--module-path=$javafx_path_value" >> "$config_path"
    echo "java-options=--add-modules=javafx.controls" >> "$config_path"
    tail -n "$tail_lines" "${config_path}.tmp" >> "$config_path"
    
    rm "${config_path}.tmp" "${config_path}.bak"
}

# Main script

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

APP_NAME="Connect Four FX"
NORMALIZED_VERSION=$(normalize_version "$APP_VERSION")
ARCHIVE_VERSION="${APP_VERSION:-$NORMALIZED_VERSION}"

# Auto-detect classifier if not specified
if [ -z "$JAVAFX_CLASSIFIER" ]; then
    if grep -qi "Darwin" /etc/os-release 2>/dev/null || [[ "$OSTYPE" == "darwin"* ]]; then
        # Detect Apple Silicon vs Intel on macOS
        if [[ $(uname -m) == "arm64" ]]; then
            JAVAFX_CLASSIFIER="mac-aarch64"
        else
            JAVAFX_CLASSIFIER="mac"
        fi
    else
        echo "Error: Could not infer JAVAFX_CLASSIFIER for this operating system."
        exit 1
    fi
fi

# Parse platform info
IFS='|' read -r FAMILY BUNDLE_NAME ARCHIVE_SUFFIX JAVAFX_PATH_VALUE <<< "$(get_platform_info "$JAVAFX_CLASSIFIER")"

JAVAC=$(resolve_java_command "$JAVA_HOME" "javac")
JAR=$(resolve_java_command "$JAVA_HOME" "jar")
JPACKAGE=$(resolve_java_command "$JAVA_HOME" "jpackage")

# Find JavaFX JARs
JAVAFX_JARS=""
for search_dir in "$JAVAFX_LIB" "$PROJECT_ROOT/javafx-sdk/lib"; do
    if [ -d "$search_dir" ]; then
        JAVAFX_JARS=$(get_javafx_jars "$search_dir" "$JAVAFX_CLASSIFIER") && break
    fi
done

if [ -z "$JAVAFX_JARS" ]; then
    echo "Error: Could not find JavaFX runtime jars for classifier '$JAVAFX_CLASSIFIER'."
    echo "Set JAVAFX_LIB or place the jars in '$PROJECT_ROOT/javafx-sdk/lib'."
    exit 1
fi

BUILD_ROOT="$PROJECT_ROOT/build/package"
CLASSES_DIR="$BUILD_ROOT/classes"
INPUT_DIR="$BUILD_ROOT/input"
JAVAFX_INPUT_DIR="$INPUT_DIR/javafx"
DIST_DIR="$PROJECT_ROOT/dist"
APP_DIR="$DIST_DIR/$BUNDLE_NAME"
ZIP_PATH="$DIST_DIR/$APP_NAME-$ARCHIVE_VERSION-$ARCHIVE_SUFFIX.zip"
MODULE_PATH=$(echo "$JAVAFX_JARS" | tr ':' ':')

# Clean up
rm -rf "$BUILD_ROOT" "$APP_DIR" "$ZIP_PATH"

# Create directories
mkdir -p "$CLASSES_DIR" "$INPUT_DIR" "$JAVAFX_INPUT_DIR" "$DIST_DIR"

# Compile
echo "Compiling ConnectFourFX.java..."
"$JAVAC" --module-path "$MODULE_PATH" --add-modules javafx.controls -d "$CLASSES_DIR" "ConnectFourFX.java"

# Copy CSS
cp "connect-four.css" "$CLASSES_DIR/"

# Create JAR
echo "Creating JAR..."
"$JAR" --create --file "$INPUT_DIR/ConnectFourFX.jar" --main-class "ConnectFourFX" -C "$CLASSES_DIR" .

# Copy JavaFX JARs
if [ -n "$JAVAFX_JARS" ]; then
    for jar in ${JAVAFX_JARS//:/ }; do
        cp "$jar" "$JAVAFX_INPUT_DIR/"
    done
fi

# Run jpackage
echo "Packaging app..."
"$JPACKAGE" \
    --type app-image \
    --dest "$DIST_DIR" \
    --input "$INPUT_DIR" \
    --name "$APP_NAME" \
    --app-version "$NORMALIZED_VERSION" \
    --vendor "Westo" \
    --description "Desktop Connect Four with a JavaFX UI and AI opponent." \
    --main-jar "ConnectFourFX.jar" \
    --main-class "ConnectFourFX" \
    --java-options "--module-path=$JAVAFX_PATH_VALUE" \
    --java-options "--add-modules=javafx.controls"

# Post-process: copy JavaFX into app bundle and rewrite config
if [ "$FAMILY" = "macos" ]; then
    APP_JAVAFX_DIR="$APP_DIR/Contents/app/javafx"
    CONFIG_PATH="$APP_DIR/Contents/app/$APP_NAME.cfg"
else
    APP_JAVAFX_DIR="$APP_DIR/app/javafx"
    CONFIG_PATH="$APP_DIR/app/$APP_NAME.cfg"
fi

mkdir -p "$APP_JAVAFX_DIR"
cp "$JAVAFX_INPUT_DIR"/* "$APP_JAVAFX_DIR/"
rewrite_launcher_config "$CONFIG_PATH" "$JAVAFX_PATH_VALUE"

# Create ZIP
echo "Creating archive..."
if [ "$FAMILY" = "macos" ]; then
    ditto -c -k --sequesterRsrc --keepParent "$APP_DIR" "$ZIP_PATH"
else
    cd "$DIST_DIR"
    zip -r -q "$(basename "$ZIP_PATH")" "$BUNDLE_NAME"
    cd "$PROJECT_ROOT"
fi

echo ""
echo "Packaged desktop app created at:"
echo "$APP_DIR"
echo ""
echo "Portable zip created at:"
echo "$ZIP_PATH"
