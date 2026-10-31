import numpy as np

from app.similarity import cosine_similarity, mean_similarity


def test_cosine_similarity_normalized_vectors():
    assert cosine_similarity(np.array([1.0, 0.0]), np.array([1.0, 0.0])) == 1.0
    assert round(cosine_similarity(np.array([1.0, 0.0]), np.array([0.0, 1.0])), 6) == 0.0


def test_mean_similarity_uses_multiple_embeddings():
    stored = np.array([1.0, 0.0])
    score = mean_similarity([np.array([1.0, 0.0]), np.array([0.8, 0.6])], stored)
    assert 0.89 < score < 0.91
