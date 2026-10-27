from __future__ import annotations

import math
from dataclasses import dataclass

import cv2
import numpy as np


@dataclass(frozen=True)
class Pose:
    yaw: float
    pitch: float


@dataclass(frozen=True)
class QualityResult:
    accepted: bool
    score: float
    message: str
    pose: Pose
    pose_bin: str


def estimate_pose(kps: object, frame_shape: tuple[int, ...]) -> Pose:
    """Approximate head yaw/pitch from the 5 InsightFace keypoints.

    Uses where the nose sits between the eyes and the mouth, normalized by the face size, so the
    result does not depend on where the face is in the frame. (A solvePnP fit with a generic 3D
    model reported about 20 degrees of yaw for a frontal face that was simply off center.)
    Signs keep the previous convention: nose toward image right -> positive yaw, nose lower -> negative pitch.
    """
    points = np.asarray(kps, dtype=np.float64)
    if points.shape != (5, 2):
        return Pose(0.0, 0.0)
    # InsightFace kps order: left eye, right eye, nose, left mouth, right mouth.
    left_eye, right_eye, nose, left_mouth, right_mouth = points
    eyes = (left_eye + right_eye) / 2
    mouth = (left_mouth + right_mouth) / 2
    eye_distance = float(np.linalg.norm(right_eye - left_eye))
    face_height = float(mouth[1] - eyes[1])
    if eye_distance < 1 or face_height < 1:
        return Pose(0.0, 0.0)
    horizontal = (nose[0] - (eyes[0] + mouth[0]) / 2) / eye_distance
    vertical = (nose[1] - eyes[1]) / face_height - 0.495  # 0.495: nose position in the frontal ArcFace template
    yaw = math.degrees(math.atan2(horizontal, 0.45))
    pitch = -math.degrees(math.atan2(vertical, 0.45))
    return Pose(float(yaw), float(pitch))


def evaluate_face(frame: np.ndarray, face: object, configuration: object) -> QualityResult:
    bbox = np.asarray(getattr(face, "bbox", [0, 0, 0, 0]), dtype=int)
    if bbox.size != 4:
        return QualityResult(False, 0.0, "No se pudo leer el encuadre facial.", Pose(0, 0), "front")
    x1, y1, x2, y2 = bbox.tolist()
    width = max(0, min(frame.shape[1], x2) - max(0, x1))
    height = max(0, min(frame.shape[0], y2) - max(0, y1))
    if min(width, height) < configuration.face_min_size:
        return QualityResult(False, 0.1, "Acérquese un poco a la cámara.", Pose(0, 0), "front")

    crop = frame[max(0, y1):min(frame.shape[0], y2), max(0, x1):min(frame.shape[1], x2)]
    if crop.size == 0:
        return QualityResult(False, 0.0, "No se pudo analizar el rostro.", Pose(0, 0), "front")
    gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
    brightness = float(np.mean(gray))
    # Measure sharpness at the recognition model's scale (112 px tall); on the full-size crop a
    # close face from a 720p webcam always looked "blurry" because each pixel changes very little.
    sample = cv2.resize(gray, (max(1, round(gray.shape[1] * 112 / gray.shape[0])), 112), interpolation=cv2.INTER_AREA)
    blur = float(cv2.Laplacian(sample, cv2.CV_64F).var())
    det_score = float(getattr(face, "det_score", 0.0))
    pose = estimate_pose(getattr(face, "kps", []), frame.shape)
    if det_score < 0.5:
        return QualityResult(False, 0.2, "Mantenga el rostro visible.", pose, "front")
    if brightness < configuration.brightness_min:
        return QualityResult(False, 0.25, "Hay poca iluminación en el rostro.", pose, "front")
    if brightness > configuration.brightness_max:
        return QualityResult(False, 0.25, "Reduzca la luz directa sobre el rostro.", pose, "front")
    if blur < configuration.blur_threshold:
        return QualityResult(False, 0.3, "Mantenga la cabeza quieta para enfocar.", pose, "front")
    if abs(pose.yaw) > configuration.max_yaw:
        return QualityResult(False, 0.35, "Reduzca el giro de la cabeza.", pose, "front")
    if abs(pose.pitch) > configuration.max_pitch:
        return QualityResult(False, 0.35, "Mire al frente de la cámara.", pose, "front")

    quality = min(
        1.0,
        max(
            0.0,
            0.35 * det_score
            + 0.25 * min(1.0, blur / max(configuration.blur_threshold * 4, 1))
            + 0.40,
        ),
    )
    if pose.yaw < -8:
        pose_bin = "left"
    elif pose.yaw > 8:
        pose_bin = "right"
    else:
        pose_bin = "front"
    return QualityResult(True, quality, "Rostro válido.", pose, pose_bin)
