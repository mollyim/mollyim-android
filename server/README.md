# EMMA Translation Server

Linux translation server with Kyber-1024 post-quantum encryption.

## Features

- **MarianMT/OPUS Translation**: Danish-to-English neural machine translation
- **Kyber-1024 Encryption**: Post-quantum key exchange (stub implementation)
- **AES-256-GCM**: Encrypted translation requests/responses
- **mDNS Discovery**: Automatic service advertisement
- **Key Rotation**: 5-minute session key rotation

## Installation

```bash
# Install Python dependencies
pip3 install -r requirements.txt

# Download OPUS-MT model (auto-downloads on first run)
python3 translation_server.py
```

## Usage

### Start Server

```bash
python3 translation_server.py
```

Server will:
1. Load OPUS-MT Danish-English model
2. Advertise via mDNS as `_emma-translate._tcp.local.`
3. Listen on port 8888
4. Handle encrypted translation requests

### Configuration

Edit `translation_server.py`:

```python
server = TranslationServer(
    host="0.0.0.0",      # Listen on all interfaces
    port=8888,            # Port number
    model_name="Helsinki-NLP/opus-mt-da-en"  # Translation model
)
```

### Client Discovery

Android client will automatically discover server via mDNS:

```kotlin
val client = NetworkTranslationClient(context)
client.startDiscovery()  // Discovers _emma-translate._tcp servers
```

## Protocol

### 1. Key Exchange (Stub)

**Production (TODO):**
- Client generates Kyber-1024 keypair
- Client sends public key to server
- Server encapsulates shared secret
- Server sends encapsulated key to client
- Both derive AES-256 session key

**Current (Stub):**
- Server generates random 32-byte session key
- Uses for AES-256-GCM encryption

### 2. Translation Request

```json
{
    "text": "Hej, hvordan har du det?",
    "source_lang": "da",
    "target_lang": "en"
}
```

**Encrypted with AES-256-GCM:**
- 12-byte IV prepended
- 128-bit auth tag appended

### 3. Translation Response

```json
{
    "translated_text": "Hi, how are you?",
    "confidence": 0.9,
    "model": "Helsinki-NLP/opus-mt-da-en"
}
```

**Encrypted with AES-256-GCM**

## Performance

### Pixel 6A Offloading

- **On-device**: ~100-400ms (INT8 quantized)
- **Network**: ~50-150ms + network latency
- **Recommended**: Use network for batch translations

### Server Hardware

- **CPU**: ~50ms per translation (8-core)
- **GPU**: ~10ms per translation (CUDA)

## Security

### Current Implementation

- ✅ AES-256-GCM encrypted requests/responses
- ✅ 5-minute key rotation
- ✅ mDNS service discovery
- ⚠️ Kyber-1024 key exchange (stub)

### Production Requirements

1. **Implement Real Kyber-1024**:
   - Use `liboqs` (Open Quantum Safe)
   - Or `pqcrypto` Python library

2. **Certificate Pinning**:
   - Verify server identity
   - Prevent MITM attacks

3. **Rate Limiting**:
   - Prevent translation abuse
   - DDoS protection

4. **Secure Key Storage**:
   - Use hardware security module (HSM)
   - Or Linux kernel keyring

## Deployment

### Systemd Service

Create `/etc/systemd/system/emma-translate.service`:

```ini
[Unit]
Description=EMMA Translation Server
After=network.target

[Service]
Type=simple
User=emma
WorkingDirectory=/opt/emma/server
ExecStart=/usr/bin/python3 /opt/emma/server/translation_server.py
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl enable emma-translate
sudo systemctl start emma-translate
```

### Docker

```bash
docker build -t emma-translate:latest .
docker run -p 8888:8888 emma-translate:latest
```

## Monitoring

### Logs

```bash
journalctl -u emma-translate -f
```

### Metrics

Server logs:
- Translation requests per second
- Average translation time
- Active client connections
- Key rotation events

## Troubleshooting

### Model Not Loading

```
ERROR: Failed to load model
```

**Solution**: Install transformers and torch:
```bash
pip3 install transformers torch
```

### mDNS Not Working

```
WARNING: mDNS advertisement disabled
```

**Solution**: Install zeroconf:
```bash
pip3 install zeroconf
```

### Permission Denied on Port 8888

```
ERROR: [Errno 13] Permission denied
```

**Solution**: Use port > 1024 or run with sudo (not recommended)

## License

AGPL-3.0 (same as parent project)
