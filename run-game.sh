#!/bin/bash
# Run the JavaFX desktop game locally

set -e

# Function to resolve Java command
resolve_java_command() {
    local command="$1"
    
    if [ -n "$JAVA_HOME" ] && [ -f "$JAVA_HOME/bin/$command" ]; then
        echo "$JAVA_HOME/bin/$command"
        return 0
    fi
    
    if command -v "$command" &> /dev/null; then
        command -v "$command"
        return 0
    fi
    
    echo "Error: Could not find '$command'. Set JAVA_HOME to a JDK 17+ install and try again." >&2
    exit 1
}

# Function to find JavaFX JARs
find_javafx_jars() {
    local classifier="$1"
    local search_dir="$2"
    
    if [ -z "$search_dir" ] || [ ! -d "$search_dir" ]; then
        return 1
    fi
    
    local base=$(find "$search_dir" -maxdepth 1 \( -name "javafx.base.jar" -o -name "javafx-base-*-$classifier.jar" \) | head -1)
    local graphics=$(find "$search_dir" -maxdepth 1 \( -name "javafx.graphics.jar" -o -name "javafx-graphics-*-$classifier.jar" \) | head -1)
    local controls=$(find "$search_dir" -maxdepth 1 \( -name "javafx.controls.jar" -o -name "javafx-controls-*-$classifier.jar" \) | head -1)
    
    if [ -n "$base" ] && [ -n "$graphics" ] && [ -n "$controls" ]; then
        echo "$base:$graphics:$controls"
        return 0
    fi
    
    return 1
}

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

JAVAC=$(resolve_java_command "javac")
JAVA=$(resolve_java_command "java")

# Auto-detect classifier
if [[ "$OSTYPE" == "darwin"* ]]; then
    if [[ $(uname -m) == "arm64" ]]; then
        CLASSIFIER="mac-aarch64"
    else
        CLASSIFIER="mac"
    fi
else
    CLASSIFIER="linux"
fi

# Find JavaFX JARs
JAVAFX_JARS=""
for search_dir in "${JAVAFX_LIB:-.}" "$PROJECT_ROOT/javafx-sdk/lib"; do
    JAVAFX_JARS=$(find_javafx_jars "$CLASSIFIER" "$search_dir") && break
done

if [ -z "$JAVAFX_JARS" ]; then
    echo "Error: Could not find JavaFX runtime jars for classifier '$CLASSIFIER'."
    echo "Set JAVAFX_LIB environment variable or place the jars in '$PROJECT_ROOT/javafx-sdk/lib'."
    exit 1
fi

echo "Compiling..."
"$JAVAC" --module-path "$JAVAFX_JARS" --add-modules javafx.controls ConnectFourFX.java

echo "Running Connect Four FX..."
"$JAVA" --module-path "$JAVAFX_JARS" --add-modules javafx.controls ConnectFourFX
