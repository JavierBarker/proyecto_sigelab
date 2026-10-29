from __future__ import annotations

import logging
import math
import time
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np

from .camera import open_camera_session
from .config import Settings, settings
from .crypto import normalize
from .errors import (
    CameraFrameUnavailableError,
    CaptureCancelledError,
    CaptureTimeoutError,
    ModelUnavailableError,
    PreviewUnavailableError,
)
from .liveness import LivenessSession
from .preview import FaceOverlay, PreviewWindow
from .quality import QualityResult, evaluate_face


logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class CaptureResult:
    embeddings: list[np.ndarray]
    quality_score: float
    frames_evaluated: int
    liveness_passed: bool
    challenge: str
    last_frame: np.ndarray | None = None


@dataclass(frozen=True)
class EnrollmentPhase:
    name: str
    action: str
    target: str
    samples: int
    timeout_code: str


class FaceEngine:
    def __init__(self, configuration: Settings = settings):
        self.settings = configuration
        self.model = None
        self.model_loaded = False
        self.dimension: int | None = None
        try:
            import onnxruntime as ort
            from insightface.app import FaceAnalysis

            providers = self._resolve_providers(ort)
            model_was_available = self._model_is_available()
            if not model_was_available:
                logger.info(
                    "El modelo %s no está en la caché local; InsightFace lo descargará durante esta primera ejecución.",
                    configuration.face_model,
                )
            self.model = FaceAnalysis(name=configuration.face_model, providers=providers)
            self.model.prepare(ctx_id=-1, det_size=(640, 640))
            self.model_loaded = True
            if not model_was_available and self._model_is_available():
                logger.info("Modelo %s descargado y listo para reutilizarse.", configuration.face_model)
            logger.info("InsightFace listo con providers=%s", providers)
        except Exception as exc:
            logger.exception("No fue posible cargar InsightFace")
            raise ModelUnavailableError(f"No fue posible cargar el modelo facial: {exc}") from exc

    def _model_is_available(self) -> bool:
        model_directory: Path = self.settings.model_directory
        return model_directory.is_dir() and any(model_directory.glob("*.onnx"))

    def _resolve_providers(self, ort: object) -> list[str]:
        available = list(ort.get_available_providers())
        requested = self.settings.onnx_providers
        if requested == ("auto",):
            preferred = ["CoreMLExecutionProvider", "CUDAExecutionProvider", "CPUExecutionProvider"]
        else:
            preferred = list(requested)
        selected = [provider for provider in preferred if provider in available]
        if "CPUExecutionProvider" in available and "CPUExecutionProvider" not in selected:
            selected.append("CPUExecutionProvider")
        if not selected:
            raise RuntimeError(f"ONNX Runtime no ofrece providers utilizables. Disponibles: {available}")
        return selected

    def detect(self, frame: np.ndarray) -> list[object]:
        if not self.model_loaded or self.model is None:
            raise ModelUnavailableError()
        return list(self.model.get(frame))

    @staticmethod
    def embedding(face: object) -> np.ndarray:
        value = getattr(face, "normed_embedding", None)
        if value is None:
            value = getattr(face, "embedding", None)
        if value is None:
            raise ValueError("El modelo no devolvió un embedding facial.")
        return normalize(np.asarray(value, dtype=np.float32))

    def capture_enrollment(self, user_label: str = "Usuario") -> CaptureResult:
        phases = (
            EnrollmentPhase("FRENTE", "MIRE AL FRENTE", "front", 6, "TIMEOUT_FRONT"),
            EnrollmentPhase("IZQUIERDA", "GIRE LIGERAMENTE A LA IZQUIERDA", "left", 5, "TIMEOUT_LEFT"),
            EnrollmentPhase("DERECHA", "GIRE LIGERAMENTE A LA DERECHA", "right", 5, "TIMEOUT_RIGHT"),
            EnrollmentPhase("FRENTE FINAL", "MIRE AL FRENTE NUEVAMENTE", "front", 4, "TIMEOUT_FRONT"),
        )
        logger.info("Enrollment phase=FRONT user_id_label=%s", user_label)
        return self._capture(
            sample_count=self.settings.enroll_sample_count,
            timeout_seconds=self.settings.enroll_timeout_seconds,
            title="SIGELAB - Registro Facial",
            user_label=user_label,
            phases=phases,
        )

    def capture_verification(self, user_label: str = "Usuario") -> CaptureResult:
        phases = (EnrollmentPhase("VERIFICACIÓN", "MIRE AL FRENTE", "front", self.settings.verify_sample_count, "TIMEOUT_FRONT"),)
        logger.info("Verification phase=FRONT user_id_label=%s", user_label)
        return self._capture(
            sample_count=self.settings.verify_sample_count,
            timeout_seconds=self.settings.verify_timeout_seconds,
            title="SIGELAB - Verificación Facial",
            user_label=user_label,
            phases=phases,
        )

    def _capture(
        self,
        sample_count: int,
        timeout_seconds: int,
        title: str,
        user_label: str,
        phases: tuple[EnrollmentPhase, ...],
    ) -> CaptureResult:
        embeddings: list[np.ndarray] = []
        qualities: list[float] = []
        phase_counts = [0 for _ in phases]
        phase_index = 0
        started = time.monotonic()
        phase_started = started
        last_sample = 0.0
        last_frame: np.ndarray | None = None
        liveness: LivenessSession | None = None
        window = PreviewWindow(title)

        with open_camera_session(self.settings) as (capture, selected_camera):
            if not self.settings.show_preview:
                raise PreviewUnavailableError(
                    "La sesión facial interactiva requiere SHOW_PREVIEW=true; no se capturará a ciegas."
                )
            logger.info(
                "Interactive camera session PID=%s index=%s name=%s backend=%s",
                __import__("os").getpid(),
                selected_camera.index,
                selected_camera.name,
                selected_camera.backend_name,
            )
            try:
                self._countdown(capture, window, title, user_label)
                while time.monotonic() - started <= timeout_seconds:
                    ok, frame = capture.read()
                    if not ok or frame is None:
                        raise CameraFrameUnavailableError()
                    last_frame = frame.copy()
                    current_phase = phases[phase_index]
                    if time.monotonic() - phase_started > max(22.0, timeout_seconds / len(phases) + 8.0):
                        message = self._phase_timeout_message(current_phase)
                        self._show_failure(window, last_frame, title, user_label, message)
                        raise CaptureTimeoutError(message, current_phase.timeout_code)

                    faces = self.detect(frame)
                    face = faces[0] if len(faces) == 1 else None
                    quality: QualityResult | None = None
                    phase_ok = False
                    action = current_phase.action
                    quality_text = "NO DETECTADA"
                    if len(faces) == 0:
                        action = "MUESTRE SU ROSTRO DENTRO DEL RECUADRO"
                    elif len(faces) > 1:
                        action = "SOLO DEBE APARECER UNA PERSONA"
                        quality_text = "INVÁLIDA: MÚLTIPLES ROSTROS"
                    else:
                        quality = evaluate_face(frame, face, self.settings)
                        quality_text = self._quality_text(quality)
                        phase_ok, pose_message = self._phase_match(quality, current_phase.target)
                        action = pose_message
                        if liveness is None and quality.accepted:
                            # Stable turn challenges are easier to understand and test than blink.
                            liveness = LivenessSession(self.settings.max_yaw, self.settings.max_pitch, False)
                        if liveness is not None and quality.accepted:
                            liveness.observe(face, quality.pose.yaw, quality.pose.pitch)
                            if not liveness.completed:
                                action = self._liveness_action(liveness)
                            elif phase_ok and time.monotonic() - last_sample >= self.settings.sample_interval_seconds:
                                if phase_counts[phase_index] < current_phase.samples:
                                    embeddings.append(self.embedding(face))
                                    qualities.append(quality.score)
                                    phase_counts[phase_index] += 1
                                    last_sample = time.monotonic()
                                    logger.info(
                                        "Enrollment phase=%s samples=%s/%s",
                                        current_phase.name,
                                        phase_counts[phase_index],
                                        current_phase.samples,
                                    )

                    overlays = self._overlays(faces, face, quality, phase_ok)
                    lines = self._lines(
                        title,
                        user_label,
                        faces,
                        quality_text,
                        action,
                        current_phase,
                        phase_counts,
                        phases,
                        liveness,
                        quality,
                    )
                    key = window.show(frame, overlays, lines)
                    if key == 27:
                        raise CaptureCancelledError()

                    if phase_counts[phase_index] >= current_phase.samples:
                        completed_lines = lines[:]
                        completed_lines[3] = f"ACCIÓN: {current_phase.action}"
                        completed_lines.append("✓ FASE COMPLETADA")
                        key = window.show(frame, overlays, completed_lines)
                        if key == 27:
                            raise CaptureCancelledError()
                        time.sleep(0.7)
                        phase_index += 1
                        if phase_index >= len(phases):
                            logger.info("Enrollment completed samples=%s", len(embeddings))
                            return CaptureResult(
                                embeddings=embeddings,
                                quality_score=float(np.mean(qualities)),
                                frames_evaluated=len(embeddings),
                                liveness_passed=bool(liveness and liveness.completed),
                                challenge=liveness.challenge if liveness else "",
                                last_frame=last_frame,
                            )
                        phase_started = time.monotonic()
                        logger.info("Enrollment phase=%s", phases[phase_index].name)

                current_phase = phases[phase_index]
                message = self._phase_timeout_message(current_phase)
                self._show_failure(window, last_frame, title, user_label, message)
                raise CaptureTimeoutError(message, current_phase.timeout_code)
            finally:
                window.close()

    def _countdown(self, capture: cv2.VideoCapture, window: PreviewWindow, title: str, user_label: str) -> None:
        deadline = time.monotonic() + 2.0
        while time.monotonic() < deadline:
            ok, frame = capture.read()
            if not ok or frame is None:
                raise CameraFrameUnavailableError("La cámara no entregó un frame durante el inicio.")
            remaining = max(1, math.ceil(deadline - time.monotonic()))
            key = window.show(
                frame,
                [],
                [
                    title.upper(),
                    f"Usuario: {user_label}",
                    "COLOQUE SU ROSTRO DENTRO DEL RECUADRO",
                    f"COMENZANDO... {remaining}",
                    "ESC - CANCELAR",
                ],
            )
            if key == 27:
                raise CaptureCancelledError()

    @staticmethod
    def _show_failure(
        window: PreviewWindow,
        frame: np.ndarray | None,
        title: str,
        user_label: str,
        message: str,
    ) -> None:
        if frame is None:
            return
        deadline = time.monotonic() + 1.2
        while time.monotonic() < deadline:
            key = window.show(
                frame,
                [],
                [title.upper(), f"Usuario: {user_label}", "CAPTURA NO COMPLETADA", message.upper(), "ESC - CERRAR"],
            )
            if key == 27:
                break

    @staticmethod
    def _phase_timeout_message(phase: EnrollmentPhase) -> str:
        return {
            "front": "No se pudo completar la captura mirando al frente.",
            "left": "No se pudo completar la captura mirando hacia la izquierda.",
            "right": "No se pudo completar la captura mirando hacia la derecha.",
        }.get(phase.target, f"No se pudo completar la fase {phase.name}.")

    @staticmethod
    def _quality_text(quality: QualityResult) -> str:
        if quality.accepted:
            return "BUENA"
        message = quality.message.upper()
        if "OSCUR" in message:
            return "DEMASIADO OSCURA"
        if "BORROS" in message or "ENFOC" in message:
            return "BORROSA"
        if "ACÉRQUESE" in message or "ACERQUESE" in message:
            return "ACÉRQUESE"
        return message

    @staticmethod
    def _phase_match(quality: QualityResult, target: str) -> tuple[bool, str]:
        if not quality.accepted:
            return False, quality.message.upper()
        yaw = quality.pose.yaw
        if target == "front":
            if abs(yaw) <= 10:
                return True, "MIRE AL FRENTE"
            return False, "VUELVA A MIRAR AL FRENTE"
        if target == "left":
            if yaw <= -12:
                return True, "MANTENGA EL GIRO A LA IZQUIERDA"
            return False, "GIRE LIGERAMENTE A LA IZQUIERDA"
        if target == "right":
            if yaw >= 12:
                return True, "MANTENGA EL GIRO A LA DERECHA"
            return False, "GIRE LIGERAMENTE A LA DERECHA"
        return False, "SIGA LA INSTRUCCIÓN EN PANTALLA"

    @staticmethod
    def _liveness_action(liveness: LivenessSession) -> str:
        if liveness.neutral_frames < 3:
            return "PRUEBA DE VIDA: MIRE AL FRENTE"
        return "PRUEBA DE VIDA: " + liveness.instruction.upper()

    @staticmethod
    def _overlays(
        faces: list[object],
        face: object | None,
        quality: QualityResult | None,
        phase_ok: bool,
    ) -> list[FaceOverlay]:
        if len(faces) > 1:
            return [FaceOverlay(item, (0, 0, 255)) for item in faces]
        if face is None:
            return []
        color = (0, 220, 0) if quality is not None and quality.accepted and phase_ok else (0, 220, 255)
        return [FaceOverlay(face, color)]

    def _lines(
        self,
        title: str,
        user_label: str,
        faces: list[object],
        quality_text: str,
        action: str,
        phase: EnrollmentPhase,
        phase_counts: list[int],
        phases: tuple[EnrollmentPhase, ...],
        liveness: LivenessSession | None,
        quality: QualityResult | None,
    ) -> list[str]:
        total = sum(item.samples for item in phases)
        completed = sum(phase_counts)
        detected = "DETECTADO" if len(faces) == 1 else "NO DETECTADO" if not faces else "INVÁLIDO"
        lines = [
            title.upper(),
            f"Usuario: {user_label}",
            f"Rostro: {detected}",
            f"Calidad: {quality_text}",
            f"Acción: {action}",
            f"Fase: {phase.name}",
            f"Muestras: {completed} / {total}   Fase: {phase_counts[phases.index(phase)]} / {phase.samples}",
            f"Liveness: {'CORRECTO' if liveness and liveness.completed else 'PENDIENTE'}",
            "ESC - CANCELAR",
        ]
        if self.settings.facial_debug and quality is not None:
            lines.insert(5, f"Yaw: {quality.pose.yaw:.1f}  Pitch: {quality.pose.pitch:.1f}")
        return lines

    def show_result(self, capture: CaptureResult, title: str, lines: list[str], duration: float = 1.5) -> None:
        if capture.last_frame is None:
            return
        window = PreviewWindow(title)
        deadline = time.monotonic() + duration
        try:
            while time.monotonic() < deadline:
                key = window.show(capture.last_frame, [], lines)
                if key == 27:
                    break
        finally:
            window.close()
