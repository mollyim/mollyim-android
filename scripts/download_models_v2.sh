#!/usr/bin/env bash
# EMMA Model Downloader - Production Version

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODELS_DIR="$SCRIPT_DIR/../models"
MODEL_FILE="$MODELS_DIR/opus-mt-da-en-int8.bin"

mkdir -p "$MODELS_DIR"

echo "=== EMMA Model Downloader (Production) ==="
echo ""

# Check prerequisites
if ! command -v python3 &> /dev/null; then
    echo "ERROR: Python 3 required"
    exit 1
fi

# Check if already downloaded
if [ -f "$MODEL_FILE" ]; then
    SIZE=$(du -h "$MODEL_FILE" | cut -f1)
    echo "✓ Model exists: $MODEL_FILE ($SIZE)"
    echo ""
    read -p "Re-download? (y/N): " -r
    [[ ! $REPLY =~ ^[Yy]$ ]] && exit 0
fi

echo "Downloading OPUS-MT Danish-English + INT8 quantization"
echo ""

# Download using Python
python3 - <<'EOF'
import sys
import os

print("Checking dependencies...")
try:
    from transformers import MarianMTModel, MarianTokenizer
    import torch
except ImportError:
    print("\nERROR: Missing dependencies")
    print("Install with: pip3 install transformers torch sentencepiece")
    sys.exit(1)

print("✓ Dependencies OK\n")

model_name = "Helsinki-NLP/opus-mt-da-en"
output_path = os.path.join(os.path.dirname(__file__), "../models/opus-mt-da-en-int8.bin")

print(f"Downloading {model_name}...")
tokenizer = MarianTokenizer.from_pretrained(model_name)
model = MarianMTModel.from_pretrained(model_name)
print("✓ Downloaded\n")

print("Quantizing to INT8...")
quantized = torch.quantization.quantize_dynamic(
    model, {torch.nn.Linear}, dtype=torch.qint8
)
print("✓ Quantized\n")

print(f"Saving to {output_path}...")
os.makedirs(os.path.dirname(output_path), exist_ok=True)
torch.save({
    'model': quantized.state_dict(),
    'config': model.config.to_dict(),
}, output_path)

tokenizer.save_pretrained(output_path.replace('.bin', '_tokenizer'))

size_mb = os.path.getsize(output_path) / (1024**2)
print(f"✓ Saved ({size_mb:.1f}MB)\n")
EOF

[ $? -eq 0 ] && echo "✓ Model ready!" || echo "✗ Download failed"
