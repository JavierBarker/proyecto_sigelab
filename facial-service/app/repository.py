from __future__ import annotations

import logging
from datetime import datetime
from typing import Any

from .db import Database


logger = logging.getLogger(__name__)


class FacialRepository:
    def __init__(self, database: Database):
        self.database = database

    def get_user(self, user_id: int) -> dict[str, Any] | None:
        sql = """
            SELECT u.Id,u.NombreUsuario,r.Nombre,CASE WHEN u.Estado='ACTIVO' THEN 1 ELSE 0 END
            FROM dbo.Usuario u JOIN dbo.Rol r ON r.Id=u.RolId
            WHERE u.Id = ?
        """
        with self.database.cursor() as cursor:
            row = cursor.execute(sql, user_id).fetchone()
        if row is None:
            return None
        return {
            "id_usuario": int(row[0]),
            "usuario": str(row[1]),
            "rol": str(row[2]),
            "activo": bool(row[3]),
        }

    def get_profile(self, user_id: int) -> dict[str, Any] | None:
        sql = """
            SELECT id_perfil_facial, id_usuario, embedding, modelo, dimension,
                   fecha_registro, fecha_actualizacion, activo,
                   cantidad_muestras, quality_score, version_algoritmo
            FROM PerfilFacial
            WHERE id_usuario = ? AND activo = 1
        """
        with self.database.cursor() as cursor:
            row = cursor.execute(sql, user_id).fetchone()
        if row is None:
            return None
        return {
            "id_perfil_facial": int(row[0]),
            "id_usuario": int(row[1]),
            "embedding": bytes(row[2]),
            "modelo": str(row[3]),
            "dimension": int(row[4]),
            "fecha_registro": row[5],
            "fecha_actualizacion": row[6],
            "activo": bool(row[7]),
            "cantidad_muestras": int(row[8]),
            "quality_score": float(row[9]) if row[9] is not None else None,
            "version_algoritmo": str(row[10]),
        }

    def save_profile(
        self,
        user_id: int,
        embedding: bytes,
        model: str,
        dimension: int,
        sample_count: int,
        quality_score: float,
        algorithm_version: str,
    ) -> None:
        update = """
            UPDATE PerfilFacial
            SET embedding = ?, modelo = ?, dimension = ?,
                fecha_actualizacion = DATEADD(HOUR,-6,SYSUTCDATETIME()), activo = 1,
                cantidad_muestras = ?, quality_score = ?, version_algoritmo = ?
            WHERE id_usuario = ?
        """
        insert = """
            INSERT INTO PerfilFacial
                (id_usuario, embedding, modelo, dimension, fecha_registro,
                 fecha_actualizacion, activo, cantidad_muestras, quality_score,
                 version_algoritmo)
            VALUES (?, ?, ?, ?, DATEADD(HOUR,-6,SYSUTCDATETIME()), DATEADD(HOUR,-6,SYSUTCDATETIME()), 1, ?, ?, ?)
        """
        with self.database.cursor() as cursor:
            updated = cursor.execute(
                update,
                embedding,
                model,
                dimension,
                sample_count,
                quality_score,
                algorithm_version,
                user_id,
            ).rowcount
            if updated == 0:
                cursor.execute(
                    insert,
                    user_id,
                    embedding,
                    model,
                    dimension,
                    sample_count,
                    quality_score,
                    algorithm_version,
                )

    def delete_profile(self, user_id: int) -> bool:
        with self.database.cursor() as cursor:
            return cursor.execute(
                "DELETE FROM PerfilFacial WHERE id_usuario = ?", user_id
            ).rowcount > 0

    def write_audit(
        self,
        user_id: int | None,
        action: str,
        result: str,
        score: float | None,
        threshold: float | None,
        detail: str,
        device: str,
    ) -> None:
        sql = """
            INSERT INTO AuditoriaFacial
                (id_usuario, fecha_hora, accion, resultado, score,
                 threshold, detalle, dispositivo)
            VALUES (?, DATEADD(HOUR,-6,SYSUTCDATETIME()), ?, ?, ?, ?, ?, ?)
        """
        try:
            with self.database.cursor() as cursor:
                cursor.execute(
                    sql,
                    user_id,
                    action,
                    result,
                    score,
                    threshold,
                    detail[:1000],
                    device[:200],
                )
        except Exception:
            # An audit failure must never expose a DB stack trace or hide the
            # original facial result. It is still logged for operations.
            logger.exception("No fue posible registrar auditoría facial")


def utc_or_database_datetime(value: Any) -> str | None:
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.isoformat()
    return str(value)
