import numpy as np
import pytest

from app.crypto import decrypt_embedding, encrypt_embedding


def test_embedding_round_trip_is_encrypted_and_normalized():
    key = b"0123456789abcdef0123456789abcdef"
    original = np.array([3.0, 4.0], dtype=np.float32)
    payload = encrypt_embedding(original, key)
    assert payload != original.tobytes()
    restored = decrypt_embedding(payload, key, 2)
    assert np.allclose(restored, np.array([0.6, 0.8], dtype=np.float32))


def test_embedding_wrong_key_fails():
    key = b"0123456789abcdef0123456789abcdef"
    payload = encrypt_embedding(np.array([1.0, 0.0]), key)
    with pytest.raises(Exception):
        decrypt_embedding(payload, b"fedcba9876543210fedcba9876543210", 2)
