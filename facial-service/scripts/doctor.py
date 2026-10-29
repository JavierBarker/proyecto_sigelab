"""Read-only preflight checks for the local facial service."""

from __future__ import annotations

import importlib
import importlib.metadata
import os
import platform
import re
import shutil
import socket
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from urllib.error import URLError
from urllib.request import Request, urlopen

from scripts import ensure_macos_odbc_runtime

ensure_macos_odbc_runtime("scripts.doctor")

SUPPORTED_PYTHON = {(3, 11), (3, 12)}
SUPPORTED_SYSTEMS = {"Windows", "Darwin", "Linux"}
SUPPORTED_ARCHITECTURES = {"x86_64", "AMD64", "amd64", "arm64", "aarch64"}


@dataclass
class CheckResult:
    label: str
    status: str
    detail: str
    required: bool = True


def _result(label: str, ok: bool, detail: str, required: bool = True) -> CheckResult:
    return CheckResult(label, "OK" if ok else "FAIL", detail, required)


def _check_import(module_name: str, label: str) -> CheckResult:
    try:
        importlib.import_module(module_name)
        return _result(label, True, "import disponible")
    except Exception as exc:
        return _result(label, False, f"{type(exc).__name__}: {exc}")


def _check_opencv_gui() -> CheckResult:
    try:
        import cv2

        try:
            headless = importlib.metadata.version("opencv-python-headless")
        except importlib.metadata.PackageNotFoundError:
            headless = None
        if headless is not None:
            return _result(
                "OpenCV GUI",
                False,
                f"opencv-python-headless {headless} está instalado junto al wheel GUI; elimínelo",
            )
        gui_line = next((line.strip() for line in cv2.getBuildInformation().splitlines() if "GUI:" in line), "GUI desconocida")
        return _result("OpenCV GUI", "NONE" not in gui_line.upper(), f"{cv2.__version__}; {gui_line}")
    except Exception as exc:
        return _result("OpenCV GUI", False, f"{type(exc).__name__}: {exc}")


def _check_python() -> CheckResult:
    version = sys.version_info[:2]
    return _result(
        "Python",
        version in SUPPORTED_PYTHON,
        f"{platform.python_version()}" if version in SUPPORTED_PYTHON else
        f"{platform.python_version()} (se requiere 3.11 o 3.12)",
    )


def _check_virtualenv() -> CheckResult:
    active = sys.prefix != sys.base_prefix
    return _result(
        "Virtualenv",
        active,
        "activo" if active else "use .venv/bin/python o .venv\\Scripts\\python.exe",
    )


def _java_environment() -> dict[str, str]:
    environment = os.environ.copy()
    if platform.system() != "Darwin":
        return environment
    try:
        java_home = subprocess.check_output(
            ["/usr/libexec/java_home", "-v", "21"],
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return environment
    if java_home:
        environment["JAVA_HOME"] = java_home
        environment["PATH"] = f"{java_home}/bin:{environment.get('PATH', '')}"
    return environment


def _check_java() -> CheckResult:
    if shutil.which("java") is None:
        return _result("Java 21", False, "instale/configure JDK 21 y JAVA_HOME")
    try:
        completed = subprocess.run(
            ["java", "-version"], capture_output=True, text=True, timeout=5, env=_java_environment()
        )
        version_text = completed.stderr or completed.stdout
        major = re.search(r'version "(\d+)', version_text)
        ok = completed.returncode == 0 and major is not None and major.group(1) == "21"
        return _result("Java 21", ok, version_text.splitlines()[0] if version_text else "sin versión")
    except (OSError, subprocess.SubprocessError) as exc:
        return _result("Java 21", False, str(exc))


def _check_maven() -> CheckResult:
    if shutil.which("mvn") is None:
        return _result("Maven", False, "instale Maven y asegure que mvn esté en PATH")
    try:
        completed = subprocess.run(
            ["mvn", "-version"], capture_output=True, text=True, timeout=10, env=_java_environment()
        )
        lines = (completed.stdout or completed.stderr).splitlines()
        java_line = next((line for line in lines if "Java version:" in line), "")
        ok = completed.returncode == 0 and "Java version: 21" in java_line
        detail = java_line or (lines[0] if lines else "sin versión")
        return _result("Maven", ok, detail if ok else f"{detail}; configure Maven con JDK 21")
    except (OSError, subprocess.SubprocessError) as exc:
        return _result("Maven", False, str(exc))


def _check_env() -> CheckResult:
    env_file = Path(__file__).resolve().parents[1] / ".env"
    return _result(".env", env_file.is_file(), "archivo encontrado" if env_file.is_file() else
                   "copie .env.example a .env y complete los valores locales")


def _check_key(configuration: object) -> CheckResult:
    try:
        configuration.encryption_key_bytes()
        return _result("Encryption key", True, "FACIAL_ENCRYPTION_KEY válida")
    except ValueError as exc:
        return _result("Encryption key", False, str(exc))


def _check_onnx() -> list[CheckResult]:
    try:
        import onnxruntime as ort

        providers = ort.get_available_providers()
        cpu_ok = "CPUExecutionProvider" in providers
        return [
            _result("ONNX Runtime", True, "import disponible"),
            _result("CPUExecutionProvider", cpu_ok, ", ".join(providers) if providers else "no disponible"),
        ]
    except Exception as exc:
        return [_result("ONNX Runtime", False, f"{type(exc).__name__}: {exc}"),
                _result("CPUExecutionProvider", False, "requiere ONNX Runtime")]


def _check_odbc(configuration: object) -> CheckResult:
    try:
        import pyodbc

        drivers = pyodbc.drivers()
        driver_ok = any(driver.lower() == configuration.db_driver.lower() for driver in drivers)
        return _result(
            "ODBC Driver 18",
            driver_ok,
            configuration.db_driver if driver_ok else "instale Microsoft ODBC Driver 18 for SQL Server",
        )
    except Exception as exc:
        return _result("ODBC Driver 18", False, f"{type(exc).__name__}: {exc}")


def _check_database(configuration: object) -> list[CheckResult]:
    try:
        from app.db import Database
    except Exception as exc:
        detail = f"no se pudo cargar DB: {type(exc).__name__}: {exc}"
        return [_result("SQL Server", False, detail),
                _result("Database", False, "requiere el módulo de base de datos"),
                _result("Facial tables", False, "requiere conexión a SQL Server")]
    database = Database(configuration)
    database_ok = database.ping()
    schema_ok = database.facial_tables_ready() if database_ok else False
    return [
        _result("SQL Server", database_ok, "conexión aceptada" if database_ok else
                "no responde; revise Docker/servicio, credenciales y ODBC"),
        _result("Database", database_ok, configuration.db_name if database_ok else
                "revise DB_HOST, DB_PORT, DB_NAME, DB_USER y DB_PASSWORD"),
        _result("Facial tables", schema_ok, "PerfilFacial y AuditoriaFacial" if schema_ok else
                "ejecute python -m scripts.migrate"),
    ]


def _check_model(configuration: object) -> CheckResult:
    directory = configuration.model_directory
    available = directory.is_dir() and any(directory.glob("*.onnx"))
    if available:
        return _result("Facial model", True, f"{configuration.face_model} en caché")
    return CheckResult(
        "Facial model",
        "WAIT",
        f"{configuration.face_model} se descargará en la primera ejecución; no se guarda en el repositorio",
        required=False,
    )


def _prepare_model(configuration: object) -> CheckResult:
    try:
        from app.engine import FaceEngine

        FaceEngine(configuration)
        return _result("Model preparation", True, f"{configuration.face_model} cargado")
    except Exception as exc:
        return _result("Model preparation", False, f"{type(exc).__name__}: {exc}")


def _check_camera_suite(configuration: object) -> list[CheckResult]:
    """Require an actual frame and a successful HighGUI presentation."""
    try:
        import cv2

        from app.camera import open_camera_session
        from app.errors import CaptureCancelledError, FacialServiceError
        from app.preview import PreviewWindow

        with open_camera_session(configuration) as (capture, selected):
            frame_ok, frame = capture.read()
            detected = _result(
                "Camera detected",
                True,
                f"índice {selected.index}, {selected.name}",
            )
            frames = _result(
                "Camera frames",
                frame_ok and frame is not None,
                f"{int(capture.get(cv2.CAP_PROP_FRAME_WIDTH))}x{int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))}"
                if frame_ok and frame is not None else "no se recibió un frame",
            )
            selected_result = _result(
                "Camera selected",
                True,
                f"{selected.index} - {selected.name} - {selected.backend_name} ({selected.reason})",
            )
            if not frames.status == "OK":
                return [detected, frames, selected_result, _result("Preview GUI", False, "requiere un frame")]
            window = PreviewWindow("Universidad Asistencia - Doctor Preview")
            try:
                try:
                    window.show(frame, [], ["DOCTOR PREVIEW", "Ventana GUI disponible", "ESC - Cerrar"])
                    cv2.waitKey(250)
                except CaptureCancelledError:
                    # Closing a window that was successfully visible is not a GUI failure.
                    pass
                preview = _result("Preview GUI", True, "imshow/waitKey OK")
            except FacialServiceError as exc:
                preview = _result("Preview GUI", False, exc.message)
            finally:
                window.close()
            return [detected, frames, selected_result, preview]
    except FacialServiceError as exc:
        return [
            _result("Camera detected", False, exc.message),
            _result("Camera frames", False, "no disponible"),
            _result("Camera selected", False, "no disponible"),
            _result("Preview GUI", False, "no se pudo abrir la cámara"),
        ]
    except Exception as exc:
        detail = f"{type(exc).__name__}: {exc}"
        return [
            _result("Camera detected", False, detail),
            _result("Camera frames", False, "no disponible"),
            _result("Camera selected", False, "no disponible"),
            _result("Preview GUI", False, detail),
        ]


def _check_port(configuration: object) -> CheckResult:
    address = (configuration.facial_host, configuration.facial_port)
    try:
        with urlopen(Request(configuration.facial_base_url + "/health", headers={"X-API-Key": os.getenv("FACIAL_API_TOKEN", "")}), timeout=2) as response:
            return _result(f"Port {configuration.facial_port}", 200 <= response.status < 300, "servicio facial responde")
    except (URLError, TimeoutError):
        pass
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            probe.bind(address)
        except OSError:
            return _result(f"Port {configuration.facial_port}", False,
                           f"{configuration.facial_host}:{configuration.facial_port} está ocupado")
    return _result(f"Port {configuration.facial_port}", True, "disponible para el servicio facial")


def run_checks(prepare_model: bool = False) -> list[CheckResult]:
    results = [
        CheckResult("OS", "OK" if platform.system() in SUPPORTED_SYSTEMS else "FAIL", platform.system()),
        CheckResult("Architecture", "OK" if platform.machine() in SUPPORTED_ARCHITECTURES else "FAIL",
                    platform.machine()),
        _check_python(),
        _check_virtualenv(),
        _check_java(),
        _check_maven(),
        _check_env(),
        _check_import("cv2", "OpenCV"),
        _check_opencv_gui(),
        _check_import("insightface", "InsightFace"),
        *_check_onnx(),
    ]
    try:
        configuration = importlib.import_module("app.config").settings
    except Exception as exc:
        results.append(_result("Configuration", False, f"{type(exc).__name__}: {exc}"))
        return results
    if prepare_model:
        results.append(_prepare_model(configuration))
    results.extend([
        _check_odbc(configuration),
        *_check_database(configuration),
        _check_model(configuration),
        *_check_camera_suite(configuration),
        _check_key(configuration),
        _check_port(configuration),
    ])
    return results


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(description="Diagnóstico read-only del servicio facial.")
    parser.add_argument(
        "--prepare-model",
        action="store_true",
        help="Carga InsightFace y permite descargar buffalo_l durante el setup; no modifica datos de SQL.",
    )
    args = parser.parse_args()
    print("SIGELAB Facial Service Doctor")
    results = run_checks(prepare_model=args.prepare_model)
    for item in results:
        print(f"{item.label:<25} {item.status:<5} {item.detail}")
    failures = [item for item in results if item.status == "FAIL" and item.required]
    pending = [item for item in results if item.status == "WAIT"]
    if failures:
        print("\nNOT READY")
        print("Corrija los elementos FAIL y vuelva a ejecutar este diagnóstico.")
        raise SystemExit(1)
    if pending:
        print("\nNOT READY")
        print("Inicie el servicio una vez para descargar el modelo y vuelva a ejecutar el diagnóstico.")
        raise SystemExit(1)
    print("\nREADY TO RUN")


if __name__ == "__main__":
    main()
