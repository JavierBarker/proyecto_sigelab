from __future__ import annotations

import logging
import os
import platform
import shutil
import subprocess
from contextlib import contextmanager, closing
from pathlib import Path
from typing import Iterator


def _configure_macos_odbc_runtime() -> None:
    """Expose Homebrew OpenSSL to Microsoft's macOS ODBC driver when needed."""
    if platform.system() != "Darwin" or shutil.which("brew") is None:
        return
    try:
        openssl_lib = subprocess.check_output(
            ["brew", "--prefix", "openssl@3"], text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return
    lib_path = str(Path(openssl_lib) / "lib")
    existing = os.environ.get("DYLD_LIBRARY_PATH", "")
    if lib_path not in existing.split(":"):
        os.environ["DYLD_LIBRARY_PATH"] = ":".join(filter(None, (lib_path, existing)))


_configure_macos_odbc_runtime()


from .config import Settings, settings


logger = logging.getLogger(__name__)


class Database:
    def __init__(self, configuration: Settings = settings):
        self.settings = configuration

    def connect(self) -> pyodbc.Connection:
        import pyodbc
        return pyodbc.connect(self.settings.connection_string, timeout=5)

    def ping(self) -> bool:
        try:
            with closing(self.connect()) as connection:
                connection.execute("SELECT 1").fetchone()
            return True
        except Exception:
            logger.exception("SQL Server no está disponible")
            return False

    def facial_tables_ready(self) -> bool:
        query = """
            SELECT COUNT(*)
            FROM sys.tables
            WHERE name IN (N'PerfilFacial', N'AuditoriaFacial')
        """
        try:
            with closing(self.connect()) as connection:
                count = connection.execute(query).fetchone()[0]
            return int(count) == 2
        except Exception:
            logger.exception("No fue posible comprobar las tablas faciales")
            return False

    @contextmanager
    def cursor(self) -> Iterator[pyodbc.Cursor]:
        connection = self.connect()
        try:
            yield connection.cursor()
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()
