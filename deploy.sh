#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

DEVICE_SERIAL="${DEVICE_SERIAL:-}"
ADB="adb"

if [ -n "$DEVICE_SERIAL" ]; then
    ADB="adb -s $DEVICE_SERIAL"
fi

function usage() {
    echo "Usage: ./deploy.sh [command] [options]"
    echo ""
    echo "Commands:"
    echo "  full            - Full build and deployment"
    echo "  build           - Build APK only"
    echo "  install         - Install APK to device"
    echo "  threat N        - Simulate threat level (0-100)"
    echo "  monitor         - Start performance monitoring"
    echo "  clean           - Clean build artifacts"
    echo ""
    echo "Environment:"
    echo "  DEVICE_SERIAL   - Target device serial number"
    exit 1
}

function check_device() {
    if ! $ADB devices | grep -q "device$"; then
        echo "ERROR: No device connected"
        echo "Connect device and enable USB debugging"
        exit 1
    fi
    echo "✓ Device connected"
}

function build_apk() {
    echo "Building APK..."
    ./gradlew assembleDebug
    echo "✓ Build complete"
}

function install_apk() {
    echo "Installing APK..."
    local apk_path="app/build/outputs/apk/prodGmsWebsite/debug/Signal-Android-prodGmsWebsite-debug.apk"

    if [ ! -f "$apk_path" ]; then
        echo "ERROR: APK not found at $apk_path"
        echo "Run './deploy.sh build' first"
        exit 1
    fi

    $ADB install -r "$apk_path"
    echo "✓ APK installed"
}

function deploy_full() {
    echo "=== Full Deployment ==="
    check_device
    build_apk
    install_apk
    echo ""
    echo "✓ Deployment complete!"
}

function simulate_threat() {
    local level=$1
    if [ -z "$level" ]; then
        echo "ERROR: Threat level required (0-100)"
        exit 1
    fi

    check_device
    echo "Simulating threat level: $level%"

    # Broadcast threat simulation intent
    $ADB shell am broadcast \
        -a im.molly.security.SIMULATE_THREAT \
        --ei threat_level "$level"

    echo "✓ Threat simulation sent"
}

function monitor_performance() {
    check_device
    echo "Starting performance monitoring..."
    echo "Press Ctrl+C to stop"
    echo ""

    mkdir -p output

    local log_file="output/performance_$(date +%Y%m%d_%H%M%S).log"

    $ADB shell "while true; do
        echo '=== $(date) ==='
        dumpsys cpuinfo | head -20
        dumpsys meminfo im.molly.app | grep -A 10 'App Summary'
        dumpsys battery | grep -E 'level|temperature'
        echo ''
        sleep 5
    done" | tee "$log_file"
}

function clean_build() {
    echo "Cleaning build artifacts..."
    ./gradlew clean
    rm -rf output/
    echo "✓ Clean complete"
}

# Main command dispatcher
COMMAND="${1:-help}"

case "$COMMAND" in
    full)
        deploy_full
        ;;
    build)
        build_apk
        ;;
    install)
        check_device
        install_apk
        ;;
    threat)
        simulate_threat "$2"
        ;;
    monitor)
        monitor_performance
        ;;
    clean)
        clean_build
        ;;
    help|--help|-h|*)
        usage
        ;;
esac
