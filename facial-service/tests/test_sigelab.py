import pytest
from fastapi import HTTPException
from app import main
from app.config import Settings
from app.repository import FacialRepository
from test_service import FakeDatabase

def test_api_rejects_missing_or_wrong_token(monkeypatch):
    monkeypatch.setenv('FACIAL_API_TOKEN','x'*48)
    with pytest.raises(HTTPException): main.api_key('')
    with pytest.raises(HTTPException): main.api_key('incorrecto')
    main.api_key('x'*48)

def test_short_token_is_never_accepted(monkeypatch):
    monkeypatch.setenv('FACIAL_API_TOKEN','short')
    with pytest.raises(HTTPException): main.api_key('short')

def test_camera_busy_is_rejected():
    main.SESSION_LOCK.acquire()
    try:
        with pytest.raises(HTTPException) as exc: main.capture('verify',1)
        assert exc.value.status_code == 409
    finally: main.SESSION_LOCK.release()

def test_session_lock_is_released_on_capture_failure(monkeypatch):
    def fail(*args): raise RuntimeError('simulado')
    monkeypatch.setattr(main,'run_interactive_session',fail)
    with pytest.raises(RuntimeError): main.capture('verify',1)
    assert not main.SESSION_LOCK.locked()

def test_sigelab_user_mapping():
    user=FacialRepository(FakeDatabase(row=(7,'usuario','ENCARGADO',1))).get_user(7)
    assert user['id_usuario']==7 and user['activo'] is True and user['rol']=='ENCARGADO'

def test_database_password_is_braced(monkeypatch):
    monkeypatch.setenv('DB_PASSWORD','a;b}c')
    assert 'PWD={a;b}}c};' in Settings.from_env().connection_string

def test_binding_is_local_only(monkeypatch):
    monkeypatch.setenv('FACIAL_HOST','192.168.0.2')
    with pytest.raises(ValueError): Settings.from_env()
