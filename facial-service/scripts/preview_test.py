from __future__ import annotations

import platform
import sys

import cv2

from scripts import ensure_macos_odbc_runtime

ensure_macos_odbc_runtime("scripts.preview_test")

from app.camera import open_camera_session  # noqa: E402
from app.config import settings  # noqa: E402
from app.errors import CaptureCancelledError, FacialServiceError, PreviewUnavailableError  # noqa: E402
from app.preview import PreviewWindow  # noqa: E402


def main() -> int:
    print("PREVIEW TEST", flush=True)
    print(f"OS ............... {platform.system()}")
    print(f"Architecture ..... {platform.machine()}")
    print(f"OpenCV ........... {cv2.__version__}")
    try:
        gui = next((line.strip() for line in cv2.getBuildInformation().splitlines() if "GUI:" in line), "GUI: unknown")
        print(f"GUI .............. {gui}")
        with open_camera_session(settings) as (capture, selected):
            window = PreviewWindow("Universidad Asistencia - Preview Test")
            try:
                print(f"Camera ........... {selected.index}")
                print(f"Name ............. {selected.name}")
                print(f"Backend .......... {selected.backend_name}")
                width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH))
                height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
                print(f"Resolution ....... {width}x{height}")
                ok, frame = capture.read()
                if not ok or frame is None:
                    print("Frames ........... FAIL")
                    return 1
                print("Frames ........... OK")
                # This call is intentionally outside FastAPI and on this process's MainThread.
                window._initialize()
                print("Preview .......... OK")
                print("Press ESC to close.", flush=True)
                while True:
                    ok, frame = capture.read()
                    if not ok or frame is None:
                        print("Frames ........... FAIL")
                        return 1
                    key = window.show(frame, [], ["PREVIEW TEST", "Video en vivo", "ESC - Cerrar"])
                    if key == 27:
                        break
            finally:
                window.close()
        print("Preview status ... CLOSED")
        return 0
    except CaptureCancelledError:
        print("Preview status ... CANCELLED")
        return 0
    except KeyboardInterrupt:
        print("Preview status ... CLOSED")
        return 0
    except (PreviewUnavailableError, FacialServiceError) as exc:
        print(f"Preview .......... FAIL ({exc.message})", file=sys.stderr)
        return 1
    except Exception as exc:
        print(f"Preview .......... FAIL ({type(exc).__name__}: {exc})", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
