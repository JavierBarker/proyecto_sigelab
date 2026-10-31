from __future__ import annotations

import argparse
import contextlib
import json
import logging
import sys

from scripts import ensure_macos_odbc_runtime

ensure_macos_odbc_runtime("scripts.interactive_face")

from app.config import settings  # noqa: E402
from app.db import Database  # noqa: E402
from app.errors import FacialServiceError  # noqa: E402
from app.engine import FaceEngine  # noqa: E402
from app.repository import FacialRepository  # noqa: E402
from app.service import FaceService  # noqa: E402
from scripts.find_user import find_user  # noqa: E402


logging.basicConfig(
    stream=sys.stderr,
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger(__name__)


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Sesión facial interactiva con preview en MainThread.")
    parser.add_argument("mode", choices=("enroll", "verify"))
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--user-id", type=int)
    target.add_argument("--username")
    parser.add_argument("--replace", action="store_true")
    return parser


def main() -> int:
    args = _parser().parse_args()
    result: dict[str, object]
    try:
        user_id = args.user_id
        if args.username:
            user = find_user(args.username)
            if user is None:
                raise FacialServiceError(f"No existe el usuario: {args.username}", 404, "USUARIO_NO_EXISTE")
            user_id = int(user["userId"])
        if user_id is None or user_id <= 0:
            raise FacialServiceError("El userId debe ser positivo.", 400, "USER_ID_INVALIDO")
        logger.info("Interactive process PID=%s mode=%s user_id=%s", __import__("os").getpid(), args.mode, user_id)
        database = Database(settings)
        repository = FacialRepository(database)
        # InsightFace and all progress logs stay on stderr. stdout is one JSON object only.
        with contextlib.redirect_stdout(sys.stderr):
            engine = FaceEngine(settings)
            service = FaceService(repository, engine, settings)
            if args.mode == "enroll":
                result = service.enroll(user_id, args.replace)
            else:
                result = service.verify(user_id)
    except FacialServiceError as exc:
        logger.warning("Interactive session failed code=%s message=%s", exc.code, exc.message)
        result = {"success": False, "code": exc.code, "detail": exc.message, "statusCode": exc.status_code}
    except Exception:
        logger.exception("Interactive session failed unexpectedly")
        result = {
            "success": False,
            "code": "INTERACTIVE_ERROR",
            "detail": "No fue posible completar la sesión facial interactiva.",
            "statusCode": 500,
        }
    print(json.dumps(result, ensure_ascii=False), flush=True)
    return 0 if result.get("success") is True else 1


if __name__ == "__main__":
    raise SystemExit(main())
