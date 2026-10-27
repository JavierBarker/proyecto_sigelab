from __future__ import annotations

import logging
import time
import unicodedata
from dataclasses import dataclass

import cv2
import numpy as np

from .errors import CaptureCancelledError, PreviewUnavailableError


logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class FaceOverlay:
    face: object
    color: tuple[int, int, int]


@dataclass(frozen=True)
class CameraGuide:
    """Phone-style camera view: dark screen, oval face frame, user name on top, one message below."""

    title: str
    message: str
    state: str = "idle"  # idle | adjust | ok | done | error
    progress: float = 0.0
    detail: str = ""
    debug: str = ""
    user: str = ""


GUIDE_COLORS = {
    "idle": (215, 215, 215),
    "adjust": (0, 160, 255),
    "ok": (201, 181, 27),
    "done": (201, 181, 27),
    "error": (70, 70, 225),
}
GUIDE_BACKGROUND = (255, 255, 255)
GUIDE_TEXT = (90, 90, 90)
CANVAS_SIZE = (600, 760)  # width, height
CIRCLE_CENTER = (300, 345)
CIRCLE_RADIUS = 215


def guide_target(shape: tuple[int, ...]) -> tuple[int, int, int]:
    """Where the face should be in the camera frame: circle center and radius (same in the mirrored view)."""
    height, width = shape[:2]
    scale = height / CANVAS_SIZE[1]
    return width // 2, int(CIRCLE_CENTER[1] * scale), int(CIRCLE_RADIUS * scale)


def _ascii(text: str) -> str:
    # OpenCV's Hershey fonts only draw ASCII; "Acérquese" would show as "Ac??rquese".
    return unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode("ascii")


class PreviewWindow:
    """Small HighGUI adapter owned by the runner's main thread."""

    def __init__(self, title: str):
        self.title = title
        self.initialized = False
        self.guide_sized = False

    def _initialize(self) -> None:
        if self.initialized:
            return
        gui_line = next(
            (line.strip() for line in cv2.getBuildInformation().splitlines() if "GUI:" in line),
            "GUI: unknown",
        )
        if "NONE" in gui_line.upper():
            raise PreviewUnavailableError(
                "La instalación de OpenCV no incluye soporte GUI para mostrar la cámara."
            )
        try:
            cv2.namedWindow(self.title, cv2.WINDOW_NORMAL)
            cv2.resizeWindow(self.title, 960, 540)
            cv2.waitKey(1)
        except cv2.error as exc:
            raise PreviewUnavailableError(
                f"No se pudo inicializar la ventana de cámara ({gui_line}). "
                "Ejecute python -m scripts.preview_test para diagnosticarla."
            ) from exc
        self.initialized = True
        logger.info("Preview initialized title=%s", self.title)

    def show(
        self,
        frame: np.ndarray,
        overlays: list[FaceOverlay],
        lines: list[str],
        guide: CameraGuide | None = None,
    ) -> int:
        self._initialize()
        if guide is not None:
            if not self.guide_sized:
                cv2.resizeWindow(self.title, *CANVAS_SIZE)
                self.guide_sized = True
            return self._present(self._render_guide(frame, guide))
        preview = frame.copy()
        for overlay in overlays:
            bbox = np.asarray(getattr(overlay.face, "bbox", []), dtype=int)
            if bbox.size != 4:
                continue
            x1, y1, x2, y2 = bbox.tolist()
            cv2.rectangle(preview, (x1, y1), (x2, y2), overlay.color, 3)
        self._draw_overlay(preview, lines)
        return self._present(preview)

    def _present(self, preview: np.ndarray) -> int:
        try:
            cv2.imshow(self.title, preview)
            key = cv2.waitKey(1) & 0xFF
            if self._is_closed():
                raise CaptureCancelledError()
            return key
        except CaptureCancelledError:
            raise
        except cv2.error as exc:
            raise PreviewUnavailableError(
                "La ventana de cámara dejó de estar disponible; la captura fue detenida."
            ) from exc

    def _is_closed(self) -> bool:
        try:
            visible = cv2.getWindowProperty(self.title, cv2.WND_PROP_VISIBLE)
            return visible < 1
        except cv2.error as exc:
            raise PreviewUnavailableError("No se pudo comprobar la ventana de cámara.") from exc

    @staticmethod
    def _render_guide(frame: np.ndarray, guide: CameraGuide) -> np.ndarray:
        canvas_w, canvas_h = CANVAS_SIZE
        color = GUIDE_COLORS.get(guide.state, GUIDE_COLORS["idle"])

        # Camera mirrored like a selfie, center-cropped to the screen shape; only the circle shows it.
        view = cv2.flip(frame, 1)
        height, width = view.shape[:2]
        crop_w = min(width, int(height * canvas_w / canvas_h))
        crop_h = min(height, int(crop_w * canvas_h / canvas_w))
        x0, y0 = (width - crop_w) // 2, (height - crop_h) // 2
        photo = cv2.resize(view[y0:y0 + crop_h, x0:x0 + crop_w], (canvas_w, canvas_h))
        canvas = np.full((canvas_h, canvas_w, 3), GUIDE_BACKGROUND, dtype=np.uint8)
        mask = np.zeros((canvas_h, canvas_w), dtype=np.uint8)
        cv2.circle(mask, CIRCLE_CENTER, CIRCLE_RADIUS, 255, -1, cv2.LINE_AA)
        alpha = (mask.astype(np.float32) / 255.0)[..., None]
        canvas = (photo * alpha + canvas * (1.0 - alpha)).astype(np.uint8)

        # Ring: light track, plus an arc in the state color that grows with the samples.
        ring = CIRCLE_RADIUS + 16
        cv2.circle(canvas, CIRCLE_CENTER, ring, (235, 235, 235), 8, cv2.LINE_AA)
        if guide.state in ("done", "error", "idle"):
            sweep = 360.0
        else:
            sweep = 360.0 * max(0.08, min(1.0, guide.progress))
        cv2.ellipse(canvas, CIRCLE_CENTER, (ring, ring), 0, -90, -90 + sweep, color, 8, cv2.LINE_AA)

        # Header: small title and the user's name; ESC hint in the corner.
        heading = {"done": "Listo!", "error": "No se pudo"}.get(guide.state, guide.title.split(" - ")[-1])
        PreviewWindow._centered_text(canvas, heading, 50, 0.8, (60, 60, 60), 2)
        if guide.user:
            PreviewWindow._centered_text(canvas, guide.user, 82, 0.55, (140, 140, 140), 1)
        cv2.putText(canvas, "ESC - Cancelar", (12, 22), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (170, 170, 170), 1, cv2.LINE_AA)

        # Message under the circle.
        y = CIRCLE_CENTER[1] + ring + 70
        text_color = color if guide.state == "error" else GUIDE_TEXT
        for line in PreviewWindow._wrap(guide.message, canvas_w - 80, 0.68, 1)[:3]:
            PreviewWindow._centered_text(canvas, line, y, 0.68, text_color, 1)
            y += 30
        if guide.detail:
            PreviewWindow._centered_text(canvas, guide.detail, y + 6, 0.5, (160, 160, 160), 1)
        if guide.debug:
            cv2.putText(canvas, _ascii(guide.debug), (12, canvas_h - 14), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (160, 160, 160), 1, cv2.LINE_AA)
        return canvas

    @staticmethod
    def _wrap(text: str, max_width: int, scale: float, thickness: int) -> list[str]:
        lines: list[str] = []
        current = ""
        for word in _ascii(text).split():
            candidate = f"{current} {word}".strip()
            width = cv2.getTextSize(candidate, cv2.FONT_HERSHEY_SIMPLEX, scale, thickness)[0][0]
            if current and width > max_width:
                lines.append(current)
                current = word
            else:
                current = candidate
        if current:
            lines.append(current)
        return lines[:3]

    @staticmethod
    def _centered_text(
        frame: np.ndarray, text: str, y: int, scale: float, color: tuple[int, int, int], thickness: int
    ) -> None:
        text = _ascii(text)
        width = cv2.getTextSize(text, cv2.FONT_HERSHEY_SIMPLEX, scale, thickness)[0][0]
        x = max(10, (frame.shape[1] - width) // 2)
        cv2.putText(frame, text, (x, y), cv2.FONT_HERSHEY_SIMPLEX, scale, color, thickness, cv2.LINE_AA)

    @staticmethod
    def _draw_overlay(frame: np.ndarray, lines: list[str]) -> None:
        line_height = 29
        max_lines = min(len(lines), 14)
        panel_height = 15 + max_lines * line_height
        panel = frame[0:panel_height, 0:min(frame.shape[1], 760)]
        if panel.size:
            dark = np.zeros_like(panel)
            cv2.addWeighted(panel, 0.35, dark, 0.65, 0, panel)
        for index, line in enumerate(lines[:max_lines]):
            color = (0, 255, 255) if index == 0 else (255, 255, 255)
            cv2.putText(
                frame,
                _ascii(line),
                (18, 30 + index * line_height),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.68,
                color,
                2,
                cv2.LINE_AA,
            )

    def close(self) -> None:
        if not self.initialized:
            return
        try:
            cv2.destroyWindow(self.title)
            cv2.waitKey(1)
        except cv2.error:
            pass
        self.initialized = False
        self.guide_sized = False


def preview_probe(frame: np.ndarray, title: str = "SIGELAB - Preview Test") -> bool:
    window = PreviewWindow(title)
    try:
        window.show(frame, [], ["PREVIEW TEST", "Video en vivo detectado", "ESC - Cerrar"])
        return True
    finally:
        window.close()
