from __future__ import annotations

import numpy as np
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from secrets import token_bytes


NONCE_SIZE = 12


def normalize(vector: np.ndarray) -> np.ndarray:
    value = np.asarray(vector, dtype=np.float32).reshape(-1)
    norm = float(np.linalg.norm(value))
    if norm <= 1e-8:
        raise ValueError("El embedding facial no puede ser un vector cero.")
    return value / norm


def encrypt_embedding(vector: np.ndarray, key: bytes) -> bytes:
    normalized = normalize(vector).astype("<f4", copy=False)
    nonce = token_bytes(NONCE_SIZE)
    encrypted = AESGCM(key).encrypt(nonce, normalized.tobytes(), None)
    return nonce + encrypted


def decrypt_embedding(payload: bytes, key: bytes, dimension: int) -> np.ndarray:
    if len(payload) <= NONCE_SIZE:
        raise ValueError("El embedding cifrado está incompleto.")
    nonce, encrypted = payload[:NONCE_SIZE], payload[NONCE_SIZE:]
    raw = AESGCM(key).decrypt(nonce, encrypted, None)
    vector = np.frombuffer(raw, dtype="<f4")
    if vector.size != dimension:
        raise ValueError("La dimensión del embedding almacenado no coincide con el modelo.")
    return normalize(vector)
