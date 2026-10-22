"""
Configuration module for the WhatsApp Backend Proxy.
Loads settings from environment variables and validates security requirements.
"""

import os
from typing import Optional
from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

INSECURE_SECRET_PATTERNS = [
    "replace-with",
    "changeme",
    "secret",
    "password",
    "12345678",
    "admin",
    "default",
    "your-secret-here",
    "example",
]

SUPPORTED_WHATSAPP_PROVIDERS = {"disabled", "waha"}


class Settings(BaseSettings):
    """Application settings validated against environment variables."""

    # Server settings
    PORT: int = Field(default=8081, ge=1, le=65535)
    HOST: str = Field(default="0.0.0.0")
    ENVIRONMENT: str = Field(default="development")

    # Security: JWT configuration
    JWT_SECRET: str = Field(..., description="Secret key for signing and verifying device JWTs")
    JWT_EXPIRATION_MINUTES: int = Field(default=5, ge=1, le=1440)
    JWT_ALGORITHM: str = Field(default="HS256")

    # WhatsApp provider selection.
    # Default is intentionally "disabled" so no message can be sent until the
    # operator explicitly configures and enables a provider.
    WHATSAPP_PROVIDER: str = Field(default="disabled")

    # WAHA configuration (server-side only; never shipped to the APK).
    WAHA_ENABLE_LIVE: bool = Field(default=False)
    WAHA_BASE_URL: Optional[str] = Field(default="http://127.0.0.1:3000")
    WAHA_API_KEY: Optional[str] = Field(default=None)
    WAHA_SESSION: str = Field(default="default")
    WAHA_VERIFY_SESSION: bool = Field(default=True)

    # Legacy WA-AKG settings retained only so existing local .env files do not
    # fail to load. The WA-AKG provider is no longer selected by this service.
    WAAKG_ENABLE_LIVE: bool = Field(default=False)
    WAAKG_BASE_URL: Optional[str] = Field(default=None)
    WAAKG_API_KEY: Optional[str] = Field(default=None)

    # Operational controls
    RATE_LIMIT_PER_MINUTE: int = Field(default=60, ge=1)
    MAX_REQUEST_BODY_BYTES: int = Field(default=65536, ge=1024)
    UPSTREAM_TIMEOUT_SECONDS: float = Field(default=10.0, ge=1.0)
    IDEMPOTENCY_TTL_SECONDS: int = Field(default=86400, ge=60)

    model_config = SettingsConfigDict(
        env_file=os.path.join(os.path.dirname(__file__), ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @field_validator("JWT_SECRET")
    @classmethod
    def validate_jwt_secret_strength(cls, v: str) -> str:
        trimmed = v.strip() if v else ""
        if len(trimmed) < 32:
            raise ValueError(
                f"JWT_SECRET is too weak ({len(trimmed)} chars). "
                "It MUST be at least 32 characters long to secure device tokens. "
                'Generate a secure key with: python -c "import secrets; print(secrets.token_hex(32))"'
            )
        lower_v = trimmed.lower()
        for pattern in INSECURE_SECRET_PATTERNS:
            if pattern in lower_v:
                raise ValueError(
                    f"Insecure placeholder pattern '{pattern}' detected in JWT_SECRET. "
                    "You must provide a cryptographically strong secret."
                )
        return trimmed

    @field_validator("WHATSAPP_PROVIDER")
    @classmethod
    def validate_whatsapp_provider(cls, v: str) -> str:
        cleaned = (v or "disabled").strip().lower()
        if cleaned not in SUPPORTED_WHATSAPP_PROVIDERS:
            allowed = ", ".join(sorted(SUPPORTED_WHATSAPP_PROVIDERS))
            raise ValueError(f"WHATSAPP_PROVIDER must be one of: {allowed}")
        return cleaned

    @field_validator("WAHA_BASE_URL")
    @classmethod
    def validate_waha_base_url(cls, v: Optional[str]) -> Optional[str]:
        if v is None:
            return None
        cleaned = v.strip().rstrip("/")
        if cleaned and not (cleaned.startswith("http://") or cleaned.startswith("https://")):
            raise ValueError("WAHA_BASE_URL must begin with http:// or https://")
        return cleaned

    @field_validator("WAHA_SESSION")
    @classmethod
    def validate_waha_session(cls, v: str) -> str:
        cleaned = (v or "default").strip()
        if not cleaned:
            raise ValueError("WAHA_SESSION cannot be blank")
        if len(cleaned) > 64:
            raise ValueError("WAHA_SESSION must be 64 characters or fewer")
        if not all(ch.isalnum() or ch in "-_" for ch in cleaned):
            raise ValueError("WAHA_SESSION may only contain letters, digits, '-' and '_'")
        return cleaned

    @field_validator("WAHA_API_KEY")
    @classmethod
    def validate_waha_api_key(cls, v: Optional[str]) -> Optional[str]:
        if v is None:
            return None
        cleaned = v.strip()
        return cleaned or None

    @field_validator("WAAKG_BASE_URL")
    @classmethod
    def validate_waakg_base_url(cls, v: Optional[str]) -> Optional[str]:
        if v is not None:
            v = v.strip().rstrip("/")
            if v and not (v.startswith("http://") or v.startswith("https://")):
                raise ValueError("WAAKG_BASE_URL must begin with http:// or https://")
        return v


def get_settings() -> Settings:
    """Instantiate and return settings."""
    return Settings()
