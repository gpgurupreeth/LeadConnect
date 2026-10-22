"""
Unit tests for JWT generation, verification, and security rules.
"""

import pytest
from fastapi import HTTPException
from auth import create_device_jwt, verify_device_jwt, extract_bearer_token
from config import Settings


def test_create_and_verify_jwt_success(jwt_secret):
    token = create_device_jwt("my-android-phone-01", jwt_secret, expires_in_minutes=10)
    claims = verify_device_jwt(token, jwt_secret)

    assert claims["sub"] == "my-android-phone-01"
    assert claims["iss"] == "sales-caller-backend-proxy"
    assert claims["exp"] > claims["iat"]


def test_expired_jwt_rejected(jwt_secret):
    token = create_device_jwt("phone-exp", jwt_secret, expires_in_minutes=-5)
    with pytest.raises(HTTPException) as exc_info:
        verify_device_jwt(token, jwt_secret)
    assert exc_info.value.status_code == 401
    assert "expired" in exc_info.value.detail.lower()


def test_tampered_jwt_rejected(jwt_secret):
    token = create_device_jwt("phone-tampered", jwt_secret, expires_in_minutes=5)
    tampered = token[:-4] + "abcd"
    with pytest.raises(HTTPException) as exc_info:
        verify_device_jwt(tampered, jwt_secret)
    assert exc_info.value.status_code == 401


def test_weak_secret_fails_validation():
    # Less than 32 characters
    with pytest.raises(ValueError) as exc:
        Settings(JWT_SECRET="short-secret-key")
    assert "too weak" in str(exc.value)

    # Insecure placeholder pattern
    with pytest.raises(ValueError) as exc:
        Settings(JWT_SECRET="replace-with-a-random-32-byte-hex-string-for-local-dev-only")
    assert "Insecure placeholder" in str(exc.value)


def test_empty_device_id_fails(jwt_secret):
    with pytest.raises(ValueError):
        create_device_jwt("   ", jwt_secret)


def test_extract_bearer_token():
    assert extract_bearer_token("Bearer token123") == "token123"
    assert extract_bearer_token("bearer  token456 ") == "token456"
    assert extract_bearer_token("Basic dXNlcjpwYXNz") is None
    assert extract_bearer_token(None) is None
    assert extract_bearer_token("") is None
