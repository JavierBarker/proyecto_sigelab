from __future__ import annotations

import argparse
import json
import os
import urllib.error
import urllib.request

from scripts import ensure_macos_odbc_runtime

ensure_macos_odbc_runtime("scripts.enroll_user")

from app.config import settings
from scripts.find_user import find_user


def main() -> None:
    parser = argparse.ArgumentParser(description="Registra un usuario mediante la misma API de enrollment.")
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--user-id", type=int)
    target.add_argument("--username")
    parser.add_argument("--replace", action="store_true")
    parser.add_argument("--url", default=settings.facial_base_url)
    args = parser.parse_args()
    user_id = args.user_id
    if args.username:
        user = find_user(args.username)
        if user is None:
            raise SystemExit(f"No existe el usuario: {args.username}")
        if not user["active"]:
            raise SystemExit(f"El usuario está inactivo: {args.username}")
        user_id = user["userId"]
    body = json.dumps({"userId": user_id, "replace": args.replace}).encode("utf-8")
    request = urllib.request.Request(
        args.url.rstrip("/") + "/face/enroll",
        data=body,
        headers={"Content-Type": "application/json", "X-API-Key": os.getenv("FACIAL_API_TOKEN", "")},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=240) as response:
            print(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        print(exc.read().decode("utf-8"))
        raise SystemExit(exc.code)


if __name__ == "__main__":
    main()
