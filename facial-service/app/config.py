from __future__ import annotations

import base64
import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv


SERVICE_ROOT = Path(__file__).resolve().parents[1]
load_dotenv(SERVICE_ROOT / ".env", interpolate=False)


def _int(name: str, default: int) -> int:
    value = os.getenv(name)
    return default if value is None or value.strip() == "" else int(value)


def _optional_int(name: str) -> int | None:
    value = os.getenv(name)
    return None if value is None or value.strip() == "" else int(value)


def _float(name: str, default: float) -> float:
    value = os.getenv(name)
    return default if value is None or value.strip() == "" else float(value)


def _bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None or value.strip() == "":
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _csv(name: str, default: tuple[str, ...]) -> tuple[str, ...]:
    value = os.getenv(name)
    if value is None or value.strip() == "":
        return default
    return tuple(item.strip() for item in value.split(",") if item.strip())


def _local_host() -> str:
    value = os.getenv("FACIAL_HOST", "127.0.0.1").strip()
    if value not in {"127.0.0.1", "localhost", ""}:
        raise ValueError("FACIAL_HOST debe ser local; no se permite exponer el servicio en todas las interfaces.")
    return value or "127.0.0.1"


@dataclass(frozen=True)
class Settings:
    db_host: str
    db_port: int
    db_name: str
    db_user: str
    db_password: str
    db_driver: str
    db_encrypt: bool
    db_trust_server_certificate: bool
    facial_host: str
    facial_port: int
    camera_index: int | None
    camera_name_hint: str
    camera_width: int
    camera_height: int
    show_preview: bool
    face_model: str
    face_match_threshold: float
    face_min_size: int
    blur_threshold: float
    brightness_min: float
    brightness_max: float
    max_yaw: float
    max_pitch: float
    enroll_sample_count: int
    verify_sample_count: int
    enroll_timeout_seconds: int
    verify_timeout_seconds: int
    liveness_timeout_seconds: int
    sample_interval_seconds: float
    facial_encryption_key: str
    onnx_providers: tuple[str, ...]
    facial_debug: bool

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            db_host=os.getenv("DB_HOST", "localhost"),
            db_port=_int("DB_PORT", 1433),
            db_name=os.getenv("DB_NAME", "SIGELAB"),
            db_user=os.getenv("DB_USER", "sa"),
            db_password=os.getenv("DB_PASSWORD", ""),
            db_driver=os.getenv("DB_DRIVER", "ODBC Driver 18 for SQL Server"),
            db_encrypt=_bool("DB_ENCRYPT", True),
            db_trust_server_certificate=_bool("DB_TRUST_SERVER_CERTIFICATE", True),
                        facial_host=_local_host(),
                        facial_port=_int("FACIAL_PORT", 8765),
                        camera_index=_optional_int("CAMERA_INDEX"),
                        camera_name_hint=os.getenv("CAMERA_NAME_HINT", "").strip(),
                        camera_width=_int("CAMERA_WIDTH", 1280),
                        camera_height=_int("CAMERA_HEIGHT", 720),
            show_preview=_bool("SHOW_PREVIEW", True),
            face_model=os.getenv("FACE_MODEL", "buffalo_l"),
            face_match_threshold=_float("FACE_MATCH_THRESHOLD", 0.55),
            face_min_size=_int("FACE_MIN_SIZE", 120),
            blur_threshold=_float("BLUR_THRESHOLD", 80),
            brightness_min=_float("BRIGHTNESS_MIN", 45),
            brightness_max=_float("BRIGHTNESS_MAX", 220),
            max_yaw=_float("MAX_YAW", 28),
            max_pitch=_float("MAX_PITCH", 22),
            enroll_sample_count=_int("ENROLL_SAMPLE_COUNT", 20),
            verify_sample_count=_int("VERIFY_SAMPLE_COUNT", 7),
            enroll_timeout_seconds=_int("ENROLL_TIMEOUT_SECONDS", 60),
            verify_timeout_seconds=_int("VERIFY_TIMEOUT_SECONDS", 20),
            liveness_timeout_seconds=_int("LIVENESS_TIMEOUT_SECONDS", 15),
            sample_interval_seconds=_float("SAMPLE_INTERVAL_SECONDS", 0.15),
            facial_encryption_key=os.getenv("FACIAL_ENCRYPTION_KEY", ""),
            onnx_providers=_csv("ONNX_PROVIDERS", ("auto",)),
            facial_debug=_bool("FACIAL_DEBUG", False),
        )

    @property
    def connection_string(self) -> str:
        encrypt = "yes" if self.db_encrypt else "no"
        trust = "yes" if self.db_trust_server_certificate else "no"
        return (
            f"DRIVER={{{self.db_driver}}};"
            f"SERVER={self.db_host},{self.db_port};"
            f"DATABASE={{{self.db_name.replace(chr(125), chr(125)*2)}}};"
            f"UID={{{self.db_user.replace(chr(125), chr(125)*2)}}};PWD={{{self.db_password.replace(chr(125), chr(125)*2)}}};"
            f"Encrypt={encrypt};TrustServerCertificate={trust};"
        )

    def encryption_key_bytes(self) -> bytes:
        if not self.facial_encryption_key:
            raise ValueError("FACIAL_ENCRYPTION_KEY no está configurada.")
        try:
            key = base64.b64decode(self.facial_encryption_key, validate=True)
        except Exception as exc:  # pragma: no cover - defensive configuration guard
            raise ValueError("FACIAL_ENCRYPTION_KEY no es base64 válida.") from exc
        if len(key) not in {16, 24, 32}:
            raise ValueError("FACIAL_ENCRYPTION_KEY debe decodificar a 16, 24 o 32 bytes.")
        return key

    @property
    def model_directory(self) -> Path:
        """Directorio de caché de InsightFace, fuera del repositorio."""
        configured_home = os.getenv("INSIGHTFACE_HOME")
        root = Path(configured_home).expanduser() if configured_home else Path.home() / ".insightface"
        return root / "models" / self.face_model

    @property
    def facial_base_url(self) -> str:
        return f"http://{self.facial_host}:{self.facial_port}"


settings = Settings.from_env()
