"""Command-line helpers for the facial service."""

from __future__ import annotations

import os
import platform
import shutil
import subprocess
import sys
from pathlib import Path


def ensure_macos_odbc_runtime(module_name: str) -> None:
    """Re-exec CLI modules with Homebrew OpenSSL visible to msodbcsql18."""
    if platform.system() != "Darwin" or os.getenv("UNIVERSIDAD_ODBC_RUNTIME_READY") == "1":
        return
    if not module_name.startswith("scripts."):
        return
    brew = shutil.which("brew")
    if brew is None:
        return
    try:
        openssl_prefix = subprocess.check_output(
            [brew, "--prefix", "openssl@3"], text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return
    openssl_lib = str(Path(openssl_prefix) / "lib")
    current = os.environ.get("DYLD_LIBRARY_PATH", "")
    if openssl_lib in current.split(":"):
        return
    environment = os.environ.copy()
    environment["DYLD_LIBRARY_PATH"] = ":".join(filter(None, (openssl_lib, current)))
    environment["UNIVERSIDAD_ODBC_RUNTIME_READY"] = "1"
    os.execve(sys.executable, [sys.executable, "-m", module_name, *sys.argv[1:]], environment)
