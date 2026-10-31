from __future__ import annotations

import logging
import platform
import re
import subprocess
import threading
from contextlib import contextmanager
from dataclasses import dataclass
from typing import Iterator

import cv2

from .config import Settings, settings
from .errors import CameraBusyError, CameraUnavailableError


logger = logging.getLogger(__name__)
CAMERA_LOCK = threading.Lock()


@dataclass(frozen=True)
class CameraInfo:
    index: int
    name: str
    backend: int
    backend_name: str
    can_read_frame: bool
    reason: str = ""


def camera_backends() -> list[int]:
    names = {
        "Windows": ("CAP_DSHOW", "CAP_MSMF"),
        "Darwin": ("CAP_AVFOUNDATION",),
        "Linux": ("CAP_V4L2",),
    }
    backends: list[int] = []
    for name in names.get(platform.system(), ()):
        backend = getattr(cv2, name, None)
        if backend is not None and backend not in backends:
            backends.append(backend)
    backends.append(getattr(cv2, "CAP_ANY", 0))
    return backends


def backend_name(backend: int) -> str:
    names = {
        getattr(cv2, "CAP_AVFOUNDATION", -1): "AVFoundation",
        getattr(cv2, "CAP_DSHOW", -1): "DirectShow",
        getattr(cv2, "CAP_MSMF", -1): "Media Foundation",
        getattr(cv2, "CAP_V4L2", -1): "V4L2",
        getattr(cv2, "CAP_ANY", 0): "Any",
    }
    return names.get(backend, str(backend))


def mac_camera_names() -> list[str]:
    """Best-effort camera names without adding an AVFoundation dependency."""
    if platform.system() != "Darwin":
        return []
    try:
        completed = subprocess.run(
            ["system_profiler", "SPCameraDataType"],
            capture_output=True,
            text=True,
            timeout=5,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return []
    names: list[str] = []
    for line in completed.stdout.splitlines():
        match = re.match(r"^\s{4}([^:]+):\s*$", line)
        if match and match.group(1).strip() not in {"Camera", ""}:
            names.append(match.group(1).strip())
    return names


def _camera_name(index: int, names: list[str]) -> str:
    return names[index] if index < len(names) else f"Camera index {index}"


def _name_score(name: str, configuration: Settings) -> tuple[int, str]:
    lowered = name.lower()
    hint = configuration.camera_name_hint.lower()
    if hint and hint in lowered:
        return (-100, f"CAMERA_NAME_HINT={configuration.camera_name_hint}")
    if any(token in lowered for token in ("iphone", "continuity", "ipad")):
        return (100, "Continuity Camera queda como último fallback")
    if any(token in lowered for token in ("facetime", "macbook", "built-in", "built in", "mac camera")):
        return (0, "cámara integrada preferida")
    return (50, "cámara disponible")


def _candidate_indices(configuration: Settings, names: list[str]) -> list[tuple[int, str]]:
    if configuration.camera_index is not None:
        return [(configuration.camera_index, _camera_name(configuration.camera_index, names))]
    count = len(names) if names else 6
    candidates = [(index, _camera_name(index, names)) for index in range(count)]
    return sorted(candidates, key=lambda item: _name_score(item[1], configuration)[0])


def _open(index: int, backend: int, configuration: Settings) -> tuple[cv2.VideoCapture, bool] | None:
    capture = cv2.VideoCapture(index, backend)
    capture.set(cv2.CAP_PROP_FRAME_WIDTH, configuration.camera_width)
    capture.set(cv2.CAP_PROP_FRAME_HEIGHT, configuration.camera_height)
    if not capture.isOpened():
        capture.release()
        return None
    ok, frame = capture.read()
    if not ok or frame is None:
        capture.release()
        return None
    return capture, ok


def list_cameras(configuration: Settings = settings, max_indices: int = 8) -> list[CameraInfo]:
    names = mac_camera_names()
    indices = _candidate_indices(configuration, names)
    if configuration.camera_index is None:
        indices = indices[:max_indices]
    found: list[CameraInfo] = []
    for index, name in indices:
        for backend in camera_backends():
            opened = _open(index, backend, configuration)
            if opened is None:
                continue
            capture, can_read = opened
            try:
                actual_backend = capture.getBackendName() if hasattr(capture, "getBackendName") else backend_name(backend)
            except cv2.error:
                actual_backend = backend_name(backend)
            capture.release()
            _, reason = _name_score(name, configuration)
            if configuration.camera_index is not None:
                reason = "CAMERA_INDEX explícito"
            found.append(CameraInfo(index, name, backend, actual_backend, can_read, reason))
            break
    return found


def select_camera(configuration: Settings = settings) -> CameraInfo:
    cameras = list_cameras(configuration)
    usable = [camera for camera in cameras if camera.can_read_frame]
    if usable:
        selected = usable[0]
        logger.info(
            "Selected camera index=%s name=%s backend=%s reason=%s",
            selected.index,
            selected.name,
            selected.backend_name,
            selected.reason,
        )
        return selected
    if configuration.camera_index is not None:
        detail = f"No se pudo leer la cámara configurada {configuration.camera_index}."
    else:
        detail = "No se encontró una cámara que entregue frames."
    raise CameraUnavailableError(detail)


@contextmanager
def open_camera_session(configuration: Settings = settings) -> Iterator[tuple[cv2.VideoCapture, CameraInfo]]:
    if not CAMERA_LOCK.acquire(blocking=False):
        raise CameraBusyError()
    capture: cv2.VideoCapture | None = None
    try:
        selected = select_camera(configuration)
        opened = _open(selected.index, selected.backend, configuration)
        if opened is None:
            raise CameraUnavailableError(f"La cámara seleccionada dejó de estar disponible: {selected.name}.")
        capture, _ = opened
        logger.info(
            "Camera opened index=%s name=%s backend=%s resolution=%sx%s",
            selected.index,
            selected.name,
            selected.backend_name,
            int(capture.get(cv2.CAP_PROP_FRAME_WIDTH)),
            int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT)),
        )
        yield capture, selected
    finally:
        if capture is not None:
            capture.release()
        try:
            cv2.destroyAllWindows()
        except cv2.error:
            pass
        CAMERA_LOCK.release()


@contextmanager
def open_camera(configuration: Settings = settings) -> Iterator[cv2.VideoCapture]:
    with open_camera_session(configuration) as (capture, _):
        yield capture
