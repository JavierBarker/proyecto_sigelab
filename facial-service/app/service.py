from __future__ import annotations

import logging
import platform

from .config import Settings, settings
from .crypto import decrypt_embedding, encrypt_embedding
from .engine import FaceEngine
from .errors import FacialServiceError, ModelUnavailableError, PreviewUnavailableError
from .repository import FacialRepository, utc_or_database_datetime
from .similarity import mean_similarity


logger = logging.getLogger(__name__)


class FaceService:
    def __init__(
        self,
        repository: FacialRepository,
        engine: FaceEngine | None,
        configuration: Settings = settings,
    ):
        self.repository = repository
        self.engine = engine
        self.settings = configuration
        self.device = platform.node() or "local-webcam"

    @property
    def model_loaded(self) -> bool:
        return self.engine is not None and self.engine.model_loaded

    def health(self) -> dict[str, object]:
        database_ok = self.repository.database.ping()
        model_loaded = self.model_loaded
        return {
            "status": "ok" if database_ok and model_loaded else "degraded",
            "model": self.settings.face_model,
            "modelLoaded": model_loaded,
            "database": "ok" if database_ok else "unavailable",
            "platform": platform.system(),
            "architecture": platform.machine(),
            "detail": None if database_ok and model_loaded else "Revise el modelo y la conexión SQL Server.",
        }

    def status(self, user_id: int) -> dict[str, object]:
        user = self.repository.get_user(user_id)
        if user is None:
            raise FacialServiceError("El usuario no existe.", 404, "USUARIO_NO_EXISTE")
        profile = self.repository.get_profile(user_id)
        if profile is None:
            return {"userId": user_id, "enrolled": False}
        return {
            "userId": user_id,
            "enrolled": True,
            "registeredAt": utc_or_database_datetime(profile["fecha_registro"]),
            "updatedAt": utc_or_database_datetime(profile["fecha_actualizacion"]),
            "model": profile["modelo"],
            "dimension": profile["dimension"],
            "samples": profile["cantidad_muestras"],
        }

    def enroll(self, user_id: int, replace: bool) -> dict[str, object]:
        user = self._require_active_user(user_id)
        existing = self.repository.get_profile(user_id)
        if existing is not None and not replace:
            self._audit(user_id, "ENROLL_FALLIDO", "DENIED", None, "Ya existe un perfil; use replace=true.")
            raise FacialServiceError(
                "El usuario ya tiene un perfil facial. Confirme la actualización con replace=true.",
                409,
                "PERFIL_YA_EXISTE",
            )
        self._audit(user_id, "ENROLL_INICIADO", "STARTED", None, f"Usuario {user['usuario']}")
        logger.info("Enrollment iniciado para user_id=%s", user_id)
        try:
            engine = self._require_engine()
            capture = engine.capture_enrollment(user["usuario"])
            final_embedding = sum(capture.embeddings) / len(capture.embeddings)
            encrypted = encrypt_embedding(final_embedding, self.settings.encryption_key_bytes())
            dimension = int(final_embedding.size)
            self.repository.save_profile(
                user_id,
                encrypted,
                self.settings.face_model,
                dimension,
                capture.frames_evaluated,
                capture.quality_score,
                "rgb-arcface-v1",
            )
            self._audit(
                user_id,
                "ENROLL_EXITOSO",
                "MATCH",
                capture.quality_score,
                f"Muestras={capture.frames_evaluated}; desafío={capture.challenge}",
            )
            try:
                engine.show_result(
                    capture,
                    "SIGELAB - Registro Facial",
                    [
                        "ROSTRO REGISTRADO CORRECTAMENTE",
                        f"Usuario: {user['usuario']}",
                        f"Muestras almacenadas: {capture.frames_evaluated}",
                    ],
                )
            except PreviewUnavailableError:
                # The complete profile is already persisted; capture preview failures are fatal
                # before this point, while a result-window failure must not report a false DB error.
                logger.exception("No fue posible mostrar el resultado final del enrollment")
            logger.info("Enrollment exitoso para user_id=%s", user_id)
            return {
                "success": True,
                "userId": user_id,
                "model": self.settings.face_model,
                "dimension": dimension,
                "samples": capture.frames_evaluated,
                "qualityScore": capture.quality_score,
                "livenessPassed": capture.liveness_passed,
                "message": "Perfil facial registrado correctamente.",
            }
        except FacialServiceError as exc:
            action = "LIVENESS_FALLIDO" if exc.code == "CAPTURA_TIMEOUT" or exc.code.startswith("TIMEOUT_") else "ENROLL_FALLIDO"
            if exc.code == "CAMARA_NO_DISPONIBLE":
                action = "CAMARA_NO_DISPONIBLE"
            elif exc.code == "CAMERA_FRAME_UNAVAILABLE":
                action = "CAMARA_FRAME_FALLIDO"
            elif exc.code == "PREVIEW_UNAVAILABLE":
                action = "PREVIEW_FALLIDO"
            self._audit(user_id, action, "DENIED", None, exc.message)
            logger.warning("Enrollment fallido para user_id=%s: %s", user_id, exc.message)
            raise
        except Exception as exc:
            self._audit(user_id, "ENROLL_FALLIDO", "ERROR", None, "Fallo interno controlado")
            logger.exception("Enrollment fallido para user_id=%s", user_id)
            raise FacialServiceError("No fue posible registrar el perfil facial.", 500, "ENROLL_ERROR") from exc

    def verify(self, user_id: int) -> dict[str, object]:
        user = self._require_active_user(user_id)
        profile = self.repository.get_profile(user_id)
        if profile is None:
            self._audit(user_id, "VERIFY_FALLIDO", "DENIED", None, "Perfil facial no registrado")
            raise FacialServiceError(
                "El usuario no posee un perfil facial registrado.",
                404,
                "PERFIL_NO_REGISTRADO",
            )
        self._audit(user_id, "VERIFY_INICIADO", "STARTED", None, "Verificación en vivo")
        logger.info("Verify iniciado para user_id=%s", user_id)
        try:
            engine = self._require_engine()
            stored = decrypt_embedding(
                profile["embedding"],
                self.settings.encryption_key_bytes(),
                profile["dimension"],
            )
            capture = engine.capture_verification(user["usuario"])
            score = mean_similarity(capture.embeddings, stored)
            matched = score >= self.settings.face_match_threshold
            result = "MATCH" if matched else "NO_MATCH"
            action = "VERIFY_EXITOSO" if matched else "VERIFY_FALLIDO"
            self._audit(
                user_id,
                action,
                result,
                score,
                f"frames={capture.frames_evaluated}; desafío={capture.challenge}",
            )
            try:
                engine.show_result(
                    capture,
                    "SIGELAB - Verificación Facial",
                    [
                        "IDENTIDAD VERIFICADA" if matched else "ROSTRO NO RECONOCIDO",
                        f"Usuario: {user['usuario']}",
                        f"Resultado: {'COINCIDE' if matched else 'NO COINCIDE'}",
                    ],
                )
            except PreviewUnavailableError:
                logger.exception("No fue posible mostrar el resultado final de verification")
            logger.info("Verify user_id=%s resultado=%s score=%.4f", user_id, result, score)
            return {
                "success": True,
                "matched": matched,
                "userId": user_id,
                "score": score,
                "threshold": self.settings.face_match_threshold,
                "livenessPassed": capture.liveness_passed,
                "framesEvaluated": capture.frames_evaluated,
                "message": "Identidad verificada correctamente." if matched else "El rostro no coincide.",
            }
        except FacialServiceError as exc:
            action = "LIVENESS_FALLIDO" if exc.code == "CAPTURA_TIMEOUT" or exc.code.startswith("TIMEOUT_") else "VERIFY_FALLIDO"
            if exc.code == "CAMARA_NO_DISPONIBLE":
                action = "CAMARA_NO_DISPONIBLE"
            elif exc.code == "CAMERA_FRAME_UNAVAILABLE":
                action = "CAMARA_FRAME_FALLIDO"
            elif exc.code == "PREVIEW_UNAVAILABLE":
                action = "PREVIEW_FALLIDO"
            self._audit(user_id, action, "DENIED", None, exc.message)
            logger.warning("Verify fallido para user_id=%s: %s", user_id, exc.message)
            raise
        except Exception as exc:
            self._audit(user_id, "VERIFY_FALLIDO", "ERROR", None, "Fallo interno controlado")
            logger.exception("Verify fallido para user_id=%s", user_id)
            raise FacialServiceError("No fue posible verificar la identidad.", 500, "VERIFY_ERROR") from exc

    def delete_profile(self, user_id: int, admin_user_id: int) -> dict[str, object]:
        admin = self.repository.get_user(admin_user_id)
        if admin is None or not admin["activo"] or admin["rol"] != "ADMIN":
            raise FacialServiceError("Solo un usuario ADMIN activo puede eliminar perfiles.", 403, "ADMIN_REQUERIDO")
        target = self.repository.get_user(user_id)
        if target is None:
            raise FacialServiceError("El usuario no existe.", 404, "USUARIO_NO_EXISTE")
        deleted = self.repository.delete_profile(user_id)
        self._audit(user_id, "PERFIL_ELIMINADO", "OK", None, f"Eliminado por admin {admin_user_id}")
        return {
            "success": True,
            "userId": user_id,
            "deleted": deleted,
            "message": "Perfil facial eliminado correctamente.",
        }

    def _require_active_user(self, user_id: int) -> dict[str, object]:
        user = self.repository.get_user(user_id)
        if user is None:
            raise FacialServiceError("El usuario no existe.", 404, "USUARIO_NO_EXISTE")
        if not user["activo"]:
            raise FacialServiceError("El usuario está inactivo.", 403, "USUARIO_INACTIVO")
        return user

    def _require_engine(self) -> FaceEngine:
        if self.engine is None or not self.engine.model_loaded:
            raise ModelUnavailableError()
        return self.engine

    def _audit(self, user_id: int | None, action: str, result: str, score: float | None, detail: str) -> None:
        self.repository.write_audit(
            user_id,
            action,
            result,
            score,
            self.settings.face_match_threshold if action.startswith("VERIFY") else None,
            detail,
            self.device,
        )
