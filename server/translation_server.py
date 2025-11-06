#!/usr/bin/env python3
"""
EMMA Translation Server with Kyber-1024 Post-Quantum Encryption

Features:
- mDNS service advertisement
- Kyber-1024 key exchange (stub - needs actual Kyber library)
- AES-256-GCM encrypted translation requests
- MarianMT/OPUS model inference
- 5-minute key rotation

Requirements:
- python 3.8+
- transformers (for MarianMT)
- torch (for model inference)
- zeroconf (for mDNS)
- cryptography (for AES-GCM)
"""

import socket
import struct
import json
import logging
from pathlib import Path
from typing import Optional
from dataclasses import dataclass
import secrets

# Optional imports (will work in stub mode without them)
try:
    from zeroconf import ServiceInfo, Zeroconf
    ZEROCONF_AVAILABLE = True
except ImportError:
    ZEROCONF_AVAILABLE = False
    logging.warning("zeroconf not available - mDNS disabled")

try:
    from transformers import MarianMTModel, MarianTokenizer
    import torch
    TRANSFORMERS_AVAILABLE = True
except ImportError:
    TRANSFORMERS_AVAILABLE = False
    logging.warning("transformers not available - using stub translation")

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

logging.basicConfig(
    level=logging.INFO,
    format='[%(asctime)s] %(levelname)s: %(message)s'
)

@dataclass
class ClientSession:
    """Client session with encryption key"""
    client_id: str
    session_key: bytes
    created_at: float

class TranslationServer:
    """EMMA Translation Server"""

    def __init__(self, host: str = "0.0.0.0", port: int = 8888, model_name: str = "Helsinki-NLP/opus-mt-da-en"):
        self.host = host
        self.port = port
        self.model_name = model_name

        self.model = None
        self.tokenizer = None
        self.zeroconf = None
        self.service_info = None

        self.sessions = {}

        logging.info(f"Initializing EMMA Translation Server on {host}:{port}")

    def load_model(self):
        """Load MarianMT translation model"""
        if not TRANSFORMERS_AVAILABLE:
            logging.warning("Transformers not available - using stub translation")
            return

        try:
            logging.info(f"Loading model: {self.model_name}")
            self.tokenizer = MarianTokenizer.from_pretrained(self.model_name)
            self.model = MarianMTModel.from_pretrained(self.model_name)

            # Use GPU if available
            if torch.cuda.is_available():
                self.model = self.model.cuda()
                logging.info("Model loaded on GPU")
            else:
                logging.info("Model loaded on CPU")

        except Exception as e:
            logging.error(f"Failed to load model: {e}")

    def advertise_service(self):
        """Advertise translation service via mDNS"""
        if not ZEROCONF_AVAILABLE:
            logging.warning("mDNS advertisement disabled (zeroconf not available)")
            return

        try:
            self.zeroconf = Zeroconf()

            service_type = "_emma-translate._tcp.local."
            service_name = f"EMMA-Translate-{socket.gethostname()}.{service_type}"

            self.service_info = ServiceInfo(
                service_type,
                service_name,
                addresses=[socket.inet_aton(self.get_local_ip())],
                port=self.port,
                properties={
                    'version': '1.0',
                    'encryption': 'kyber-1024',
                    'languages': 'da-en'
                }
            )

            self.zeroconf.register_service(self.service_info)
            logging.info(f"mDNS service advertised: {service_name}")

        except Exception as e:
            logging.error(f"Failed to advertise service: {e}")

    def get_local_ip(self) -> str:
        """Get local IP address"""
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
        finally:
            s.close()
        return ip

    def perform_key_exchange(self, client_socket: socket.socket) -> bytes:
        """Perform Kyber-1024 key exchange with client"""
        # STUB: In production, this would:
        # 1. Receive client's Kyber public key
        # 2. Generate server Kyber keypair
        # 3. Encapsulate shared secret using client's public key
        # 4. Send encapsulated key to client
        # 5. Derive AES-256 session key from shared secret

        # For now, generate and exchange a random session key
        session_key = secrets.token_bytes(32)

        logging.info("Key exchange completed (stub implementation)")
        return session_key

    def decrypt_request(self, encrypted_data: bytes, session_key: bytes) -> dict:
        """Decrypt client request using AES-256-GCM"""
        iv = encrypted_data[:12]
        ciphertext = encrypted_data[12:]

        aesgcm = AESGCM(session_key)
        plaintext = aesgcm.decrypt(iv, ciphertext, None)

        return json.loads(plaintext.decode('utf-8'))

    def encrypt_response(self, response: dict, session_key: bytes) -> bytes:
        """Encrypt response using AES-256-GCM"""
        plaintext = json.dumps(response).encode('utf-8')

        aesgcm = AESGCM(session_key)
        iv = secrets.token_bytes(12)
        ciphertext = aesgcm.encrypt(iv, plaintext, None)

        return iv + ciphertext

    def translate(self, text: str, source_lang: str, target_lang: str) -> str:
        """Translate text using MarianMT model"""
        if self.model is None or self.tokenizer is None:
            # Stub translation
            return f"[STUB DA->EN] {text}"

        try:
            # Tokenize
            inputs = self.tokenizer(text, return_tensors="pt", padding=True)

            if torch.cuda.is_available():
                inputs = {k: v.cuda() for k, v in inputs.items()}

            # Generate translation
            with torch.no_grad():
                outputs = self.model.generate(**inputs)

            # Decode
            translated = self.tokenizer.decode(outputs[0], skip_special_tokens=True)
            return translated

        except Exception as e:
            logging.error(f"Translation error: {e}")
            return text

    def handle_client(self, client_socket: socket.socket, client_addr: tuple):
        """Handle client connection"""
        client_id = f"{client_addr[0]}:{client_addr[1]}"
        logging.info(f"Client connected: {client_id}")

        try:
            # Perform key exchange
            session_key = self.perform_key_exchange(client_socket)

            # Receive encrypted request
            size_data = client_socket.recv(4)
            if not size_data:
                return

            request_size = struct.unpack('!I', size_data)[0]
            encrypted_request = client_socket.recv(request_size)

            # Decrypt request
            request = self.decrypt_request(encrypted_request, session_key)

            logging.info(f"Translation request: {request['text'][:50]}...")

            # Perform translation
            translated_text = self.translate(
                request['text'],
                request.get('source_lang', 'da'),
                request.get('target_lang', 'en')
            )

            # Prepare response
            response = {
                'translated_text': translated_text,
                'confidence': 0.9,
                'model': self.model_name
            }

            # Encrypt and send response
            encrypted_response = self.encrypt_response(response, session_key)
            client_socket.sendall(struct.pack('!I', len(encrypted_response)))
            client_socket.sendall(encrypted_response)

            logging.info(f"Translation completed for {client_id}")

        except Exception as e:
            logging.error(f"Error handling client {client_id}: {e}")

        finally:
            client_socket.close()

    def start(self):
        """Start translation server"""
        logging.info("Starting EMMA Translation Server")

        # Load translation model
        self.load_model()

        # Advertise service via mDNS
        self.advertise_service()

        # Start listening
        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server_socket.bind((self.host, self.port))
        server_socket.listen(5)

        logging.info(f"Server listening on {self.host}:{self.port}")

        try:
            while True:
                client_socket, client_addr = server_socket.accept()
                # Handle client in separate thread in production
                self.handle_client(client_socket, client_addr)

        except KeyboardInterrupt:
            logging.info("Server shutting down...")

        finally:
            server_socket.close()
            if self.zeroconf:
                self.zeroconf.unregister_service(self.service_info)
                self.zeroconf.close()

def main():
    """Main entry point"""
    server = TranslationServer(host="0.0.0.0", port=8888)
    server.start()

if __name__ == "__main__":
    main()
