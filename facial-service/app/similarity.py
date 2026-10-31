from __future__ import annotations

import numpy as np

from .crypto import normalize


def cosine_similarity(left: np.ndarray, right: np.ndarray) -> float:
    a = normalize(left)
    b = normalize(right)
    return float(np.dot(a, b))


def mean_similarity(embeddings: list[np.ndarray], stored: np.ndarray) -> float:
    if not embeddings:
        raise ValueError("No hay embeddings válidos para comparar.")
    scores = [cosine_similarity(item, stored) for item in embeddings]
    return float(np.mean(scores))
