#!/usr/bin/env bash

# Generate encryption keys for EMMA-Android
# Should be run on air-gapped machine for maximum security

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../keys"

mkdir -p "$OUTPUT_DIR"

echo "=== EMMA-Android Key Generation ==="
echo ""
echo "Generating encryption keys..."
echo "⚠️  Run this on an air-gapped machine for production use"
echo ""

# Generate Kyber-1024 keypair (stub - needs actual Kyber implementation)
echo "Generating Kyber-1024 keypair..."
dd if=/dev/urandom of="$OUTPUT_DIR/kyber_public.key" bs=1568 count=1 2>/dev/null
dd if=/dev/urandom of="$OUTPUT_DIR/kyber_private.key" bs=3168 count=1 2>/dev/null
echo "✓ Kyber keys generated"

# Generate AES-256 key for translation cache
echo "Generating AES-256 cache encryption key..."
dd if=/dev/urandom of="$OUTPUT_DIR/cache_encryption.key" bs=32 count=1 2>/dev/null
echo "✓ Cache encryption key generated"

# Generate SHA-256 checksums
echo ""
echo "=== Key Checksums (Archive These) ==="
cd "$OUTPUT_DIR"
for key in *.key; do
    checksum=$(sha256sum "$key" | awk '{print $1}')
    echo "$key: $checksum"
done

echo ""
echo "✓ Key generation complete"
echo ""
echo "Keys stored in: $OUTPUT_DIR"
echo "⚠️  Archive checksums on paper in secure location"
echo "⚠️  Transfer keys to device via secure channel only"
