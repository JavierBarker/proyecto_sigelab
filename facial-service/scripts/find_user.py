from __future__ import annotations

import argparse
from typing import Any

from scripts import ensure_macos_odbc_runtime

ensure_macos_odbc_runtime("scripts.find_user")

from app.config import settings
from app.db import Database


def find_user(username: str) -> dict[str, Any] | None:
    query = """
        SELECT u.Id, u.NombreUsuario, CASE WHEN u.Estado='ACTIVO' THEN 1 ELSE 0 END,
               CASE WHEN p.id_perfil_facial IS NULL THEN 0 ELSE 1 END
        FROM Usuario AS u
        LEFT JOIN PerfilFacial AS p ON p.id_usuario = u.Id AND p.activo = 1
        WHERE u.NombreUsuario = ?
    """
    database = Database(settings)
    with database.connect() as connection:
        row = connection.execute(query, username).fetchone()
    if row is None:
        return None
    return {
        "username": str(row[1]),
        "userId": int(row[0]),
        "active": bool(row[2]),
        "facialProfile": bool(row[3]),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Busca un usuario sin modificar la base de datos.")
    parser.add_argument("username")
    args = parser.parse_args()
    result = find_user(args.username)
    if result is None:
        print(f"No existe el usuario: {args.username}")
        raise SystemExit(1)
    print(f"username: {result['username']}")
    print(f"userId: {result['userId']}")
    print(f"activo: {str(result['active']).lower()}")
    print(f"facialProfile: {str(result['facialProfile']).lower()}")


if __name__ == "__main__":
    main()
