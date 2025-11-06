#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODELS_DIR="$SCRIPT_DIR/../models"

mkdir -p "$MODELS_DIR"

echo "=== EMMA-Android Model Downloader ==="
echo ""
echo "Downloading translation models..."
echo ""

# MarianMT OPUS Danish-English INT8 Quantized Model
MODEL_URL="https://huggingface.co/Helsinki-NLP/opus-mt-da-en"
MODEL_FILE="$MODELS_DIR/opus-mt-da-en-int8.bin"

echo "📦 Model: OPUS-MT Danish-English INT8"
echo "Source: Helsinki NLP / Hugging Face"
echo ""

# NOTE: Actual model download would use Hugging Face API
# This is a stub implementation

if [ -f "$MODEL_FILE" ]; then
    echo "✓ Model already exists: $MODEL_FILE"
else
    echo "⚠️  STUB: Actual model download not implemented"
    echo ""
    echo "To download manually:"
    echo "1. Visit: $MODEL_URL"
    echo "2. Download model files"
    echo "3. Convert to INT8 quantized format"
    echo "4. Place in: $MODEL_FILE"
    echo ""
    echo "Model size: ~150MB"
    echo "Quantized size: ~40MB"

    # Create placeholder
    echo "STUB_MODEL" > "$MODEL_FILE"
    echo "✓ Created placeholder model file"
fi

echo ""
echo "Model directory: $MODELS_DIR"
echo ""
echo "To use models on device:"
echo "  adb push $MODELS_DIR/*.bin /sdcard/Android/data/im.molly.app/files/models/"
