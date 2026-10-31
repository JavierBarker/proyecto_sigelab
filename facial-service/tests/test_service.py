from contextlib import contextmanager

import pytest

from app.config import settings
from app.errors import FacialServiceError
from app.repository import FacialRepository
from app.service import FaceService


class FakeDatabase:
    def __init__(self, healthy=True, row=None):
        self.healthy = healthy
        self.row = row

    def ping(self):
        return self.healthy

    @contextmanager
    def cursor(self):
        cursor = FakeCursor(self.row)
        yield cursor


class FakeCursor:
    def __init__(self, row):
        self.row = row

    def execute(self, *_args):
        return self

    def fetchone(self):
        return self.row


class FakeRepository:
    def __init__(self, user=None, profile=None, database=None):
        self.user = user
        self.profile = profile
        self.database = database or FakeDatabase()
        self.audits = []

    def get_user(self, user_id):
        return self.user if self.user and self.user["id_usuario"] == user_id else None

    def get_profile(self, _user_id):
        return self.profile

    def write_audit(self, *args):
        self.audits.append(args)


class FakeEngine:
    model_loaded = True


def active_admin():
    return {"id_usuario": 1, "usuario": "admin", "rol": "ADMIN", "activo": True}


def test_health_reports_model_and_database():
    service = FaceService(FakeRepository(user=active_admin()), FakeEngine(), settings)
    result = service.health()
    assert result["status"] == "ok"
    assert result["modelLoaded"] is True
    assert result["database"] == "ok"


def test_status_without_profile_does_not_expose_embedding():
    service = FaceService(FakeRepository(user=active_admin()), FakeEngine(), settings)
    result = service.status(1)
    assert result == {"userId": 1, "enrolled": False}
    assert "embedding" not in result


def test_unknown_user_is_rejected():
    service = FaceService(FakeRepository(), FakeEngine(), settings)
    with pytest.raises(FacialServiceError) as error:
        service.status(999)
    assert error.value.status_code == 404
    assert error.value.code == "USUARIO_NO_EXISTE"


def test_repository_maps_facial_profile_without_changing_payload():
    row = (4, 1, b"encrypted", "buffalo_l", 512, None, None, True, 20, 0.91, "rgb-arcface-v1")
    repository = FacialRepository(FakeDatabase(row=row))
    profile = repository.get_profile(1)
    assert profile["id_perfil_facial"] == 4
    assert profile["embedding"] == b"encrypted"
    assert profile["dimension"] == 512
    assert profile["cantidad_muestras"] == 20
