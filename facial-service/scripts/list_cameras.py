from __future__ import annotations

import platform

from scripts import ensure_macos_odbc_runtime

ensure_macos_odbc_runtime("scripts.list_cameras")

from app.camera import list_cameras, select_camera  # noqa: E402
from app.config import settings  # noqa: E402


def main() -> int:
    print("CAMERAS")
    print(f"OS: {platform.system()}")
    print(f"Architecture: {platform.machine()}")
    cameras = list_cameras(settings)
    if not cameras:
        print("No se encontraron cámaras que entreguen frames.")
        return 1
    for camera in cameras:
        print(
            f"{camera.index}  {camera.name}  {camera.backend_name}  "
            f"{'YES' if camera.can_read_frame else 'NO'}  {camera.reason}"
        )
    try:
        selected = select_camera(settings)
        print(f"Recommended: {selected.index} - {selected.name}")
        print(f"Backend: {selected.backend_name}")
    except Exception as exc:
        print(f"Recommended: unavailable ({exc})")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
