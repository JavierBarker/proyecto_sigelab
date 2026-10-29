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
    points = np.asarray(kps, dtype=np.float64)
    if points.shape != (5, 2):
        return Pose(0.0, 0.0)
    # InsightFace kps order: left eye, right eye, nose, left mouth, right mouth.
    image_points = points[[2, 0, 1, 3, 4]]
    model_points = np.array(
        [
            (0.0, 0.0, 0.0),
            (-30.0, -30.0, -30.0),
            (30.0, -30.0, -30.0),
            (-25.0, 30.0, -25.0),
            (25.0, 30.0, -25.0),
        ],
        dtype=np.float64,
    )
    height, width = frame_shape[:2]
    focal = float(width)
    camera_matrix = np.array(
        [[focal, 0, width / 2], [0, focal, height / 2], [0, 0, 1]],
        dtype=np.float64,
    )
    try:
        success, rotation, _ = cv2.solvePnP(
            model_points,
            image_points,
            camera_matrix,
            np.zeros((4, 1)),
            flags=cv2.SOLVEPNP_EPNP,
        )
        if not success:
            return Pose(0.0, 0.0)
        matrix, _ = cv2.Rodrigues(rotation)
        sy = math.sqrt(matrix[0, 0] ** 2 + matrix[1, 0] ** 2)
        yaw = math.degrees(math.atan2(-matrix[2, 0], sy))
        pitch = math.degrees(math.atan2(matrix[2, 1], matrix[2, 2]))
        return Pose(float(yaw), float(pitch))
    except cv2.error:
        return Pose(0.0, 0.0)


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
    blur = float(cv2.Laplacian(gray, cv2.CV_64F).var())
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
