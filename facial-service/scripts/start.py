"""Start the local facial API using the centralized .env configuration."""

from __future__ import annotations

import uvicorn

from scripts import ensure_macos_odbc_runtime

ensure_macos_odbc_runtime("scripts.start")

from app.config import settings


def main() -> None:
    uvicorn.run(
        "app.main:app",
        host=settings.facial_host,
        port=settings.facial_port,
        log_level="info",
    )


if __name__ == "__main__":
    main()
