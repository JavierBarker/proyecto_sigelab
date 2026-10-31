from __future__ import annotations

import re
from pathlib import Path

from scripts import ensure_macos_odbc_runtime

ensure_macos_odbc_runtime("scripts.migrate")

from app.config import settings
from app.db import Database


def main() -> None:
    script = Path(__file__).resolve().parents[1] / "sql" / "001_create_facial_tables.sql"
    statements = re.split(r"(?im)^\s*GO\s*(?:--.*)?$", script.read_text(encoding="utf-8"))
    database = Database(settings)
    with database.connect() as connection:
        cursor = connection.cursor()
        for statement in statements:
            if statement.strip():
                cursor.execute(statement)
        connection.commit()
    print("Migración facial aplicada correctamente.")


if __name__ == "__main__":
    main()
