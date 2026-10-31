from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class UserRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    user_id: int = Field(alias="userId", gt=0)


class EnrollRequest(UserRequest):
    replace: bool = False


class DeleteProfileRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    admin_user_id: int = Field(alias="adminUserId", gt=0)


class FaceStatusResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    user_id: int = Field(alias="userId")
    enrolled: bool
    registered_at: str | None = Field(default=None, alias="registeredAt")
    updated_at: str | None = Field(default=None, alias="updatedAt")
    model: str | None = None
    dimension: int | None = None
    samples: int | None = None


class EnrollResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    success: bool
    user_id: int = Field(alias="userId")
    model: str | None = None
    dimension: int | None = None
    samples: int = 0
    quality_score: float | None = Field(default=None, alias="qualityScore")
    liveness_passed: bool = Field(alias="livenessPassed")
    message: str


class VerifyResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    success: bool
    matched: bool
    user_id: int = Field(alias="userId")
    score: float | None = None
    threshold: float
    liveness_passed: bool = Field(alias="livenessPassed")
    frames_evaluated: int = Field(alias="framesEvaluated")
    message: str


class HealthResponse(BaseModel):
    status: str
    model: str
    model_loaded: bool = Field(alias="modelLoaded")
    database: str
    platform: str
    architecture: str
    detail: str | None = None
    model_config = ConfigDict(populate_by_name=True)


def public_error(message: str, code: str) -> dict[str, Any]:
    return {"detail": message, "code": code}
