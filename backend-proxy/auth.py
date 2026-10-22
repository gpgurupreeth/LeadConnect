"""
Authentication and JWT verification module for the backend proxy.
Implements device token signing, validation, and claim verification.
"""

from datetime import datetime, timezone, timedelta
from typing import Optional, Dict, Any
import jwt
from fastapi import Header, HTTPException, status
from pydantic import BaseModel


class TokenClaims(BaseModel):
    sub: str
    iat: int
    exp: int
    iss: str = "sales-caller-backend-proxy"


def create_device_jwt(
    device_id: str,
    secret: str,
    expires_in_minutes: int = 5,
    algorithm: str = "HS256",
) -> str:
    """Generate a cryptographically signed, short-lived JWT for an Android device."""
    clean_device_id = device_id.strip()
    if not clean_device_id:
        raise ValueError("device_id cannot be empty")

    now = datetime.now(timezone.utc)
    expiration = now + timedelta(minutes=expires_in_minutes)

    payload = {
        "sub": clean_device_id,
        "iat": int(now.timestamp()),
        "exp": int(expiration.timestamp()),
        "iss": "sales-caller-backend-proxy",
    }

    return jwt.encode(payload, secret, algorithm=algorithm)


def verify_device_jwt(
    token: str,
    secret: str,
    algorithm: str = "HS256",
) -> Dict[str, Any]:
    """
    Verify signature, expiration, and claims of a device JWT.
    Raises HTTPException(401) on any failure.
    """
    clean_token = token.strip()
    if not clean_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or empty JWT token",
        )

    try:
        payload = jwt.decode(
            clean_token,
            secret,
            algorithms=[algorithm],
            options={"require": ["exp", "sub", "iat"]},
        )
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="JWT session token has expired. Acquire a fresh session token.",
        )
    except jwt.InvalidTokenError as err:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Invalid JWT session token: {str(err)}",
        )

    sub = payload.get("sub")
    if not sub or not isinstance(sub, str) or not sub.strip():
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="JWT token missing valid 'sub' claim (device identifier)",
        )

    return payload


def extract_bearer_token(authorization_header: Optional[str]) -> Optional[str]:
    """Safely extract Bearer token from Authorization header."""
    if not authorization_header:
        return None
    parts = authorization_header.strip().split()
    if len(parts) == 2 and parts[0].lower() == "bearer":
        return parts[1].strip()
    return None
