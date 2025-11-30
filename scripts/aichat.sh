#!/bin/bash
# AICHAT Launcher for Linux/macOS
# Automatically detects GPU and configures OpenCL environment

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Detect Java
if [ -n "$JAVA_HOME" ]; then
    JAVA="$JAVA_HOME/bin/java"
elif command -v java &> /dev/null; then
    JAVA="java"
else
    echo "Error: Java not found. Please install Java 25+ or set JAVA_HOME"
    exit 1
fi

# Check Java version
JAVA_VERSION=$("$JAVA" -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 25 ] 2>/dev/null; then
    echo "Warning: Java 25+ recommended (found: $JAVA_VERSION)"
fi

# Detect OS
OS="$(uname -s)"
case "$OS" in
    Linux*)     PLATFORM="linux"; LIB_EXT="so";;
    Darwin*)    PLATFORM="macos"; LIB_EXT="dylib";;
    *)          echo "Unsupported OS: $OS"; exit 1;;
esac

NATIVE_LIB="$SCRIPT_DIR/native/$PLATFORM/libaichat_native.$LIB_EXT"

# Configure OpenCL for different GPU vendors
configure_opencl() {
    # AMD GPU with Mesa rusticl (best open-source OpenCL 3.0)
    if [ -f /usr/lib/x86_64-linux-gnu/libMesaOpenCL.so ] || \
       [ -f /usr/lib64/libMesaOpenCL.so ]; then
        # Detect AMD GPU
        if lspci 2>/dev/null | grep -qi "AMD.*VGA\|Radeon"; then
            export RUSTICL_ENABLE="${RUSTICL_ENABLE:-radeonsi}"
            echo "Detected AMD GPU, using rusticl OpenCL"
        # Detect Intel GPU  
        elif lspci 2>/dev/null | grep -qi "Intel.*VGA\|Intel.*Graphics"; then
            export RUSTICL_ENABLE="${RUSTICL_ENABLE:-iris}"
            echo "Detected Intel GPU, using rusticl OpenCL"
        fi
    fi
    
    # NVIDIA proprietary driver
    if [ -f /usr/lib/x86_64-linux-gnu/libOpenCL.so.1 ] && \
       lspci 2>/dev/null | grep -qi "NVIDIA"; then
        echo "Detected NVIDIA GPU with proprietary driver"
    fi
    
    # ROCm for AMD (alternative to rusticl)
    if [ -d /opt/rocm ] && [ -z "$RUSTICL_ENABLE" ]; then
        export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:+$LD_LIBRARY_PATH:}/opt/rocm/lib"
        echo "Detected ROCm, using AMD OpenCL"
    fi
    
    # Intel OpenCL runtime
    if [ -d /opt/intel/opencl ]; then
        export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:+$LD_LIBRARY_PATH:}/opt/intel/opencl"
    fi
}

# Configure native library path
configure_native() {
    if [ -f "$NATIVE_LIB" ]; then
        export LD_LIBRARY_PATH="$SCRIPT_DIR/native/$PLATFORM${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
        echo "Native acceleration: enabled"
    else
        echo "Native library not found, using Java fallback"
    fi
}

# macOS specific
if [ "$PLATFORM" = "macos" ]; then
    export DYLD_LIBRARY_PATH="$SCRIPT_DIR/native/$PLATFORM${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"
fi

echo "AICHAT - Advanced Image Color Harmony Analysis"
echo "================================================"

configure_opencl
configure_native

# Build module path for JavaFX
MODULEPATH=""
for jar in "$SCRIPT_DIR"/lib/javafx-*.jar; do
    if [ -f "$jar" ]; then
        if [ -n "$MODULEPATH" ]; then
            MODULEPATH="$MODULEPATH:$jar"
        else
            MODULEPATH="$jar"
        fi
    fi
done

# Build classpath for other jars
CLASSPATH=""
for jar in "$SCRIPT_DIR"/lib/*.jar; do
    # Skip JavaFX jars in classpath (they go in modulepath)
    case "$jar" in
        */javafx-*) continue ;;
    esac
    if [ -n "$CLASSPATH" ]; then
        CLASSPATH="$CLASSPATH:$jar"
    else
        CLASSPATH="$jar"
    fi
done

# Run application with JavaFX as modules
exec "$JAVA" \
    --module-path "$MODULEPATH" \
    --add-modules javafx.controls,javafx.fxml,javafx.swing \
    --enable-native-access=javafx.graphics,ALL-UNNAMED \
    -Djava.library.path="$SCRIPT_DIR/native/$PLATFORM" \
    -cp "$CLASSPATH" \
    aichat.App "$@"
