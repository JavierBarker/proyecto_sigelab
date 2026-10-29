from __future__ import annotations

import logging
import threading
import os
import secrets
from contextlib import asynccontextmanager

from fastapi import FastAPI, Depends, Header, HTTPException
from fastapi.responses import JSONResponse

from .config import settings
from .db import Database
from .engine import FaceEngine
from .errors import FacialServiceError
from .interactive import run_interactive_session
from .repository import FacialRepository
from .schemas import (
    DeleteProfileRequest,
    EnrollRequest,
    EnrollResponse,
    FaceStatusResponse,
    HealthResponse,
    UserRequest,
    VerifyResponse,
)
from .service import FaceService


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger(__name__)


def api_key(x_api_key: str = Header(default="")):
    expected = os.getenv("FACIAL_API_TOKEN", "")
    if len(expected) < 32 or not secrets.compare_digest(x_api_key, expected):
        raise HTTPException(status_code=401, detail="Token del servicio facial inválido.")

SESSION_LOCK = threading.Lock()

def capture(mode, user_id, replace=False):
    if not SESSION_LOCK.acquire(blocking=False):
        raise HTTPException(status_code=409, detail="La cámara ya está en uso por otra sesión facial.")
    try:
        return run_interactive_session(mode, user_id, replace, settings)
    finally:
        SESSION_LOCK.release()

@asynccontextmanager
async def lifespan(app: FastAPI):
    if len(os.getenv("FACIAL_API_TOKEN", "")) < 32:
        raise RuntimeError("Configure FACIAL_API_TOKEN (mínimo 32 caracteres).")
    settings.encryption_key_bytes()
    database = Database(settings)
    repository = FacialRepository(database)
    try:
        engine = FaceEngine(settings)
    except FacialServiceError:
        logger.exception("El servicio iniciará en estado degradado: modelo no disponible")
        engine = None
    app.state.face_service = FaceService(repository, engine, settings)
    yield


app = FastAPI(
    title="SIGELAB - Facial Service",
    dependencies=[Depends(api_key)],
    version="1.0.0",
    lifespan=lifespan,
)


def service() -> FaceService:
    return app.state.face_service


@app.exception_handler(FacialServiceError)
async def facial_error_handler(_, exc: FacialServiceError):
    return JSONResponse(
        status_code=exc.status_code,
        content={"detail": exc.message, "code": exc.code},
        headers={"X-Facial-Code": exc.code},
    )


@app.get("/health", response_model=HealthResponse, response_model_by_alias=True)
def health():
    return service().health()


@app.get("/face/status/{user_id}", response_model=FaceStatusResponse, response_model_by_alias=True)
def face_status(user_id: int):
    return service().status(user_id)


@app.post("/face/enroll", response_model=EnrollResponse, response_model_by_alias=True)
def face_enroll(request: EnrollRequest):
    logger.info("POST /face/enroll thread=%s user_id=%s", threading.current_thread().name, request.user_id)
    return capture("enroll", request.user_id, request.replace)


@app.post("/face/verify", response_model=VerifyResponse, response_model_by_alias=True)
def face_verify(request: UserRequest):
    logger.info("POST /face/verify thread=%s user_id=%s", threading.current_thread().name, request.user_id)
    return capture("verify", request.user_id)


@app.delete("/face/profile/{user_id}")
def face_delete(user_id: int, request: DeleteProfileRequest):
    return service().delete_profile(user_id, request.admin_user_id)
