from __future__ import annotations

import json
import logging
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

from .config import SERVICE_ROOT, Settings, settings
from .errors import FacialServiceError


logger = logging.getLogger(__name__)


def run_interactive_session(
    mode: str,
    user_id: int,
    replace: bool = False,
    configuration: Settings = settings,
) -> dict[str, Any]:
    """Run camera/HighGUI in a child process whose main thread owns the window."""
    if mode not in {"enroll", "verify"}:
        raise ValueError(f"Modo interactivo inválido: {mode}")
    command = [
        sys.executable,
        "-m",
        "scripts.interactive_face",
        mode,
        "--user-id",
        str(user_id),
    ]
    if mode == "enroll" and replace:
        command.append("--replace")
    timeout = min(210, (configuration.enroll_timeout_seconds if mode == "enroll" else configuration.verify_timeout_seconds) + 60)
    environment = os.environ.copy()
    environment["PYTHONUNBUFFERED"] = "1"
    logger.info("Interactive process starting mode=%s user_id=%s", mode, user_id)
    try:
        completed = subprocess.run(
            command,
            cwd=Path(SERVICE_ROOT),
            env=environment,
            stdin=subprocess.DEVNULL,
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        logger.error("Interactive process timeout mode=%s user_id=%s", mode, user_id)
        raise FacialServiceError(
            "La sesión facial excedió el tiempo permitido y fue detenida.",
            408,
            "INTERACTIVE_TIMEOUT",
        ) from exc
    except OSError as exc:
        logger.exception("No fue posible iniciar el proceso interactivo")
        raise FacialServiceError(
            "No fue posible iniciar la sesión facial interactiva.",
            503,
            "INTERACTIVE_UNAVAILABLE",
        ) from exc

    if completed.stderr:
        logger.info("Interactive process stderr:\n%s", completed.stderr.rstrip())
    try:
        payload = json.loads(completed.stdout)
    except (TypeError, json.JSONDecodeError) as exc:
        logger.error("Interactive process returned invalid JSON: %r", completed.stdout[-1000:])
        raise FacialServiceError(
            "La sesión facial no devolvió un resultado estructurado.",
            500,
            "INTERACTIVE_PROTOCOL_ERROR",
        ) from exc
    if not isinstance(payload, dict):
        raise FacialServiceError("La sesión facial devolvió un resultado inválido.", 500, "INTERACTIVE_PROTOCOL_ERROR")
    if payload.get("success") is True:
        return payload
    code = str(payload.get("code") or "INTERACTIVE_ERROR")
    message = str(payload.get("detail") or "La sesión facial no se completó.")
    status_code = int(payload.get("statusCode") or _status_for_code(code))
    raise FacialServiceError(message, status_code, code)


def _status_for_code(code: str) -> int:
    if code.startswith("TIMEOUT_") or code in {"CAPTURA_TIMEOUT", "INTERACTIVE_TIMEOUT"}:
        return 408
    if code in {
        "PREVIEW_UNAVAILABLE",
        "CAMARA_NO_DISPONIBLE",
        "CAMERA_FRAME_UNAVAILABLE",
        "INTERACTIVE_UNAVAILABLE",
        "MODELO_NO_DISPONIBLE",
    }:
        return 503
    if code in {"PERFIL_YA_EXISTE"}:
        return 409
    if code in {"PERFIL_NO_REGISTRADO", "USUARIO_NO_EXISTE"}:
        return 404
    return 500
