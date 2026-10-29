from __future__ import annotations

import random
from dataclasses import dataclass

import numpy as np


@dataclass
class LivenessSession:
    max_yaw: float
    max_pitch: float
    supports_blink: bool
    challenge: str = ""
    neutral_frames: int = 0
    blink_closed: bool = False
    completed: bool = False

    def __post_init__(self) -> None:
        choices = ["turn_left", "turn_right"]
        if self.supports_blink:
            choices.append("blink")
        self.challenge = random.SystemRandom().choice(choices)

    @property
    def instruction(self) -> str:
        if self.completed:
            return "Prueba de vida completada. Mantenga el rostro visible."
        if self.neutral_frames < 3:
            return "Mire al frente para iniciar la prueba de vida."
        return {
            "turn_left": "Gire ligeramente a la izquierda.",
            "turn_right": "Gire ligeramente a la derecha.",
            "blink": "Parpadee una vez.",
        }[self.challenge]

    def observe(self, face: object, yaw: float, pitch: float) -> bool:
        if self.completed:
            return True
        if abs(yaw) <= min(10.0, self.max_yaw * 0.45) and abs(pitch) <= min(10.0, self.max_pitch * 0.45):
            self.neutral_frames = min(3, self.neutral_frames + 1)
        if self.neutral_frames < 3:
            return False

        if self.challenge == "turn_left":
            self.completed = yaw <= -max(12.0, self.max_yaw * 0.55)
        elif self.challenge == "turn_right":
            self.completed = yaw >= max(12.0, self.max_yaw * 0.55)
        else:
            openness = _eye_openness(getattr(face, "landmark_2d_106", None), getattr(face, "bbox", None))
            if openness is not None:
                if openness < 0.18:
                    self.blink_closed = True
                elif self.blink_closed and openness > 0.24:
                    self.completed = True
        return self.completed


def _eye_openness(landmarks: object, bbox: object) -> float | None:
    """Estimate eye openness without persisting frames.

    InsightFace exposes 106 landmarks for buffalo_l. The eye points are
    selected by their location inside the eye band instead of relying on a
    model-specific hard-coded index table.
    """
    if landmarks is None or bbox is None:
        return None
    points = np.asarray(landmarks, dtype=np.float32)
    box = np.asarray(bbox, dtype=np.float32)
    if points.ndim != 2 or points.shape[1] != 2 or points.shape[0] < 50 or box.size != 4:
        return None
    x1, y1, x2, y2 = box.tolist()
    center_x = (x1 + x2) / 2
    top = y1 + (y2 - y1) * 0.24
    bottom = y1 + (y2 - y1) * 0.58
    candidates = points[(points[:, 1] >= top) & (points[:, 1] <= bottom)]
    left = candidates[candidates[:, 0] < center_x]
    right = candidates[candidates[:, 0] >= center_x]
    ratios = []
    for eye in (left, right):
        if len(eye) < 3:
            continue
        horizontal = float(np.ptp(eye[:, 0]))
        vertical = float(np.ptp(eye[:, 1]))
        if horizontal > 2:
            ratios.append(vertical / horizontal)
    return float(np.mean(ratios)) if ratios else None
