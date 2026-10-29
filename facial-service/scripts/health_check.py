from __future__ import annotations

import json
import os
import urllib.error
import urllib.request

from app.config import settings


def main() -> None:
    url = settings.facial_base_url + "/health"
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"X-API-Key": os.getenv("FACIAL_API_TOKEN", "")}), timeout=10) as response:
            payload = json.loads(response.read().decode("utf-8"))
            print(json.dumps(payload, ensure_ascii=False, indent=2))
            if response.status < 200 or response.status >= 300:
                raise SystemExit(1)
            if payload.get("status") != "ok":
                raise SystemExit(1)
    except (urllib.error.URLError, TimeoutError, ValueError) as exc:
        print(f"Health no disponible en {url}: {exc}")
        raise SystemExit(1) from exc


if __name__ == "__main__":
    main()
