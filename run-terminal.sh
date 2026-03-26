#!/bin/bash
# Run the terminal Connect Four game

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

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

JAVAC=$(resolve_java_command "javac")
JAVA=$(resolve_java_command "java")

echo "Compiling..."
"$JAVAC" TerminalConnectFour.java

echo "Running Connect Four..."
echo ""
"$JAVA" TerminalConnectFour
