#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

function print_banner() {
    echo -e "${BLUE}"
    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║                  EMMA Dockerized Build System                 ║"
    echo "║          Secure Communication with Post-Quantum Crypto        ║"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

function usage() {
    cat << EOF
Usage: ./build.sh [command] [options]

${GREEN}COMMANDS:${NC}
  ${YELLOW}Android Build:${NC}
    debug              Build debug APK
    release            Build release APK
    full               Build all variants (debug + release)
    clean              Clean build artifacts
    test               Run unit tests
    benchmark          Run crypto benchmarks

  ${YELLOW}Python Server:${NC}
    server             Build and run translation server
    server-test        Test translation server
    server-stop        Stop translation server

  ${YELLOW}Development:${NC}
    dev                Start interactive development shell
    deps               Install dependencies only
    verify             Verify build environment

  ${YELLOW}Docker Management:${NC}
    build-images       Build all Docker images
    clean-docker       Clean Docker resources
    rebuild            Rebuild from scratch

${GREEN}OPTIONS:${NC}
  --production       Enable production crypto (liboqs)
  --variant=NAME     Build specific variant (prodGms, prodFoss, etc.)
  --no-cache         Build without Docker cache
  --parallel         Enable parallel builds

${GREEN}ENVIRONMENT VARIABLES:${NC}
  PRODUCTION_CRYPTO          Enable production crypto (ON/OFF, default: ON)
  CI_APP_TITLE              App title (default: EMMA)
  CI_PACKAGE_ID             Package ID (default: im.molly.app)
  CI_BUILD_VARIANTS         Build variants regex (default: prod(Gms|Foss))

${GREEN}EXAMPLES:${NC}
  ./build.sh debug                    # Build debug APK
  ./build.sh release --production     # Build production release
  ./build.sh full --variant=prodGms   # Build prodGms variant only
  ./build.sh server                   # Start translation server
  ./build.sh dev                      # Interactive development

${GREEN}OUTPUT:${NC}
  APKs:     app/build/outputs/apk/
  AABs:     app/build/outputs/bundle/
  Logs:     app/build/outputs/logs/

EOF
    exit 0
}

function log_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

function log_success() {
    echo -e "${GREEN}✓${NC} $1"
}

function log_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

function log_error() {
    echo -e "${RED}✗${NC} $1"
}

function check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed"
        echo "Install Docker: https://docs.docker.com/get-docker/"
        exit 1
    fi

    if ! docker info &> /dev/null; then
        log_error "Docker daemon is not running"
        exit 1
    fi

    log_success "Docker is available"
}

function check_docker_compose() {
    if ! command -v docker compose &> /dev/null; then
        log_error "Docker Compose is not installed"
        exit 1
    fi
    log_success "Docker Compose is available"
}

function verify_environment() {
    log_info "Verifying build environment..."

    check_docker
    check_docker_compose

    log_info "Checking system resources..."

    # Check disk space (need at least 10GB)
    available_space=$(df -BG . | awk 'NR==2 {print $4}' | sed 's/G//')
    if [ "$available_space" -lt 10 ]; then
        log_warning "Low disk space: ${available_space}GB available (recommended: 10GB+)"
    else
        log_success "Disk space OK: ${available_space}GB available"
    fi

    # Check memory
    total_mem=$(free -g | awk 'NR==2 {print $2}')
    if [ "$total_mem" -lt 8 ]; then
        log_warning "Low memory: ${total_mem}GB (recommended: 8GB+)"
    else
        log_success "Memory OK: ${total_mem}GB"
    fi

    log_success "Environment verification complete"
}

function build_android_debug() {
    log_info "Building Android debug APK..."

    docker compose run --rm \
        -e PRODUCTION_CRYPTO="${PRODUCTION_CRYPTO:-ON}" \
        android-builder assembleDebug

    log_success "Debug build complete"
    ls -lh app/build/outputs/apk/prodGmsWebsite/debug/*.apk 2>/dev/null || true
}

function build_android_release() {
    log_info "Building Android release APK..."

    if [ -z "$CI_KEYSTORE_PATH" ]; then
        log_warning "No signing key configured. APK will be unsigned."
        log_info "Set CI_KEYSTORE_PATH, CI_KEYSTORE_PASSWORD, and CI_KEYSTORE_ALIAS to sign"
    fi

    docker compose run --rm \
        -e PRODUCTION_CRYPTO="${PRODUCTION_CRYPTO:-ON}" \
        android-builder assembleRelease

    log_success "Release build complete"
    ls -lh app/build/outputs/apk/prodGmsWebsite/release/*.apk 2>/dev/null || true
}

function build_android_full() {
    log_info "Building all Android variants..."

    docker compose run --rm \
        -e PRODUCTION_CRYPTO="${PRODUCTION_CRYPTO:-ON}" \
        android-builder assemble

    log_success "Full build complete"
    find app/build/outputs/apk -name "*.apk" -exec ls -lh {} \; 2>/dev/null || true
}

function run_tests() {
    log_info "Running unit tests..."

    docker compose run --rm android-builder test

    log_success "Tests complete"
}

function run_benchmarks() {
    log_info "Running crypto benchmarks..."
    log_warning "This requires a connected Android device via ADB"

    docker compose run --rm \
        -v /dev/bus/usb:/dev/bus/usb \
        --privileged \
        android-builder connectedAndroidTest \
        --tests "im.molly.app.benchmarks.CryptoBenchmarks"

    log_success "Benchmarks complete"
}

function clean_build() {
    log_info "Cleaning build artifacts..."

    docker compose run --rm android-builder clean
    rm -rf app/build/outputs/

    log_success "Clean complete"
}

function build_server() {
    log_info "Building and starting translation server..."

    docker compose up -d --build python-server

    log_success "Translation server running on port 5353"
    log_info "View logs: docker compose logs -f python-server"
}

function test_server() {
    log_info "Testing translation server..."

    docker compose run --rm python-server pytest -v

    log_success "Server tests complete"
}

function stop_server() {
    log_info "Stopping translation server..."

    docker compose stop python-server
    docker compose rm -f python-server

    log_success "Server stopped"
}

function start_dev_shell() {
    log_info "Starting development shell..."
    log_info "You can now run Gradle commands directly"
    log_info "Example: ./gradlew assembleDebug"

    docker compose run --rm dev
}

function install_deps() {
    log_info "Installing dependencies..."

    # Build Docker images
    docker compose build android-builder
    docker compose build python-server

    log_success "Dependencies installed"
}

function build_images() {
    log_info "Building all Docker images..."

    docker compose build

    log_success "All images built"
}

function clean_docker() {
    log_info "Cleaning Docker resources..."

    docker compose down -v
    docker system prune -f

    log_success "Docker cleanup complete"
}

function rebuild_all() {
    log_info "Rebuilding everything from scratch..."

    clean_docker
    docker compose build --no-cache

    log_success "Rebuild complete"
}

# Parse arguments
COMMAND="${1:-help}"
shift || true

PRODUCTION_CRYPTO="${PRODUCTION_CRYPTO:-ON}"
DOCKER_OPTS=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --production)
            PRODUCTION_CRYPTO="ON"
            shift
            ;;
        --variant=*)
            CI_BUILD_VARIANTS="${1#*=}"
            export CI_BUILD_VARIANTS
            shift
            ;;
        --no-cache)
            DOCKER_OPTS="$DOCKER_OPTS --no-cache"
            shift
            ;;
        --parallel)
            GRADLE_OPTS="$GRADLE_OPTS -Dorg.gradle.parallel=true"
            export GRADLE_OPTS
            shift
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            ;;
    esac
done

export PRODUCTION_CRYPTO

# Execute command
print_banner

case "$COMMAND" in
    debug)
        verify_environment
        build_android_debug
        ;;
    release)
        verify_environment
        build_android_release
        ;;
    full)
        verify_environment
        build_android_full
        ;;
    clean)
        clean_build
        ;;
    test)
        verify_environment
        run_tests
        ;;
    benchmark)
        verify_environment
        run_benchmarks
        ;;
    server)
        verify_environment
        build_server
        ;;
    server-test)
        verify_environment
        test_server
        ;;
    server-stop)
        stop_server
        ;;
    dev)
        verify_environment
        start_dev_shell
        ;;
    deps)
        verify_environment
        install_deps
        ;;
    verify)
        verify_environment
        ;;
    build-images)
        verify_environment
        build_images
        ;;
    clean-docker)
        clean_docker
        ;;
    rebuild)
        rebuild_all
        ;;
    help|--help|-h)
        usage
        ;;
    *)
        log_error "Unknown command: $COMMAND"
        echo ""
        usage
        ;;
esac

log_success "Done!"
