"""
WhatsApp Backend Proxy Service for Android Sales Caller.
Provides authenticated routing, JWT verification, idempotency control,
and downstream forwarding to WAHA.
"""

import logging
import re
from contextlib import asynccontextmanager
from typing import Optional, Dict, Any
from fastapi import FastAPI, Header, HTTPException, Request, Response, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, field_validator

from config import Settings, get_settings
from auth import verify_device_jwt, extract_bearer_token
from idempotency import IdempotencyStore
from limiter import SlidingWindowRateLimiter
from upstream import IWhatsAppProvider, DisabledProvider, WahaProvider

# Configure safe structured logger (never logs tokens, secrets, or message bodies)
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("backend-proxy")

# Idempotency keys are opaque, bounded, and URL/log safe. The Android client
# generates a 64-character SHA-256 hex key, but the proxy accepts a small
# compatible alphabet for existing integrations.
IDEMPOTENCY_KEY_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{5,199}$")

# Global instances initialized during startup or module load
settings: Settings = None  # type: ignore
idempotency_store: IdempotencyStore = None  # type: ignore
rate_limiter: SlidingWindowRateLimiter = None  # type: ignore
upstream_provider: IWhatsAppProvider = None  # type: ignore


def init_app_state():
    """Initialize or reload settings and global singletons."""
    global settings, idempotency_store, rate_limiter, upstream_provider
    try:
        settings = get_settings()
    except Exception as err:
        logger.critical("Fatal configuration failure: %s", str(err))
        raise

    idempotency_store = IdempotencyStore(default_ttl_seconds=settings.IDEMPOTENCY_TTL_SECONDS)
    rate_limiter = SlidingWindowRateLimiter(
        max_requests=settings.RATE_LIMIT_PER_MINUTE,
        window_seconds=60,
    )

    if settings.WHATSAPP_PROVIDER == "waha":
        if not settings.WAHA_BASE_URL or not settings.WAHA_API_KEY:
            upstream_provider = DisabledProvider(
                reason=(
                    "WAHA is selected but WAHA_BASE_URL or WAHA_API_KEY is missing. "
                    "Live dispatch remains safely disabled."
                )
            )
            logger.warning("WAHA provider requested but required credentials are missing.")
        else:
            # Instantiate the WAHA adapter even while the kill switch is off so
            # the health endpoint can still report connectivity and session
            # status. The dispatch endpoint independently blocks every send
            # when WAHA_ENABLE_LIVE is false. No send call is made by health.
            logger.info(
                "Initializing WAHA provider for %s (session=%s, live_send=%s)",
                settings.WAHA_BASE_URL,
                settings.WAHA_SESSION,
                "enabled" if settings.WAHA_ENABLE_LIVE else "disabled",
            )
            upstream_provider = WahaProvider(
                base_url=settings.WAHA_BASE_URL,
                api_key=settings.WAHA_API_KEY,
                session=settings.WAHA_SESSION,
                timeout_seconds=settings.UPSTREAM_TIMEOUT_SECONDS,
                verify_session=settings.WAHA_VERIFY_SESSION,
            )
    else:
        upstream_provider = DisabledProvider(
            reason="WhatsApp dispatch is disabled (WHATSAPP_PROVIDER=disabled)."
        )


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifecycle manager."""
    init_app_state()
    logger.info(
        "Backend proxy starting on port %d [Env: %s]",
        settings.PORT,
        settings.ENVIRONMENT,
    )
    yield
    logger.info("Backend proxy shutting down cleanly.")


app = FastAPI(
    title="Sales Caller WhatsApp Backend Proxy",
    version="1.0.0",
    description="Secure proxy service for Android Sales Caller WhatsApp follow-ups",
    lifespan=lifespan,
)


# --- Request Models ---

class MessageRequest(BaseModel):
    """Payload sent by Android BackendProxyClient.kt."""
    sessionId: str = Field(default="default", min_length=1, max_length=100)
    jid: str = Field(..., min_length=5, max_length=120, description="Recipient WhatsApp JID")
    content: str = Field(..., min_length=1, max_length=4096, description="Message text content")
    jobId: str = Field(..., min_length=1, max_length=128, description="Unique message job idempotency ID")
    templateName: str = Field(..., min_length=1, max_length=100, description="Template identifier")
    # Consent defaults to false. A client cannot accidentally opt a recipient
    # into live messaging by omitting these fields.
    consent: bool = Field(
        default=False,
        description="Recipient has given explicit consent to receive WhatsApp messages",
    )
    optOut: bool = Field(
        default=False,
        description="Recipient has opted out of WhatsApp messaging",
    )

    @field_validator("jid")
    @classmethod
    def validate_jid_format(cls, v: str) -> str:
        clean = v.strip()
        if not clean:
            raise ValueError("JID cannot be blank")

        if "@" in clean:
            local, _, domain = clean.partition("@")
            if domain.strip().lower() not in ("s.whatsapp.net", "c.us"):
                raise ValueError("JID must end with @s.whatsapp.net or @c.us")
            digits = local
        else:
            digits = clean

        digits = digits.replace("+", "").strip()
        if not digits.isdigit():
            raise ValueError("JID must contain valid phone digits or follow recipient@s.whatsapp.net format")
        if not 7 <= len(digits) <= 15:
            raise ValueError("JID must include a country code and contain 7-15 digits")
        return clean

    @field_validator("jobId")
    @classmethod
    def validate_job_id(cls, v: str) -> str:
        clean = v.strip()
        if not clean:
            raise ValueError("jobId cannot be blank")
        return clean


class MessageResponse(BaseModel):
    """Response expected by Android BackendProxyClient.

    ``messageId`` is nullable: WAHA can accept a send without returning a
    provider message ID. The proxy must not invent a fake acknowledgement ID.
    """
    success: bool
    messageId: Optional[str] = None
    status: str


def live_send_block_reason() -> Optional[str]:
    """Return why live dispatch is blocked, or None when every gate is open.

    This is the single server-side kill switch used by both the dispatch
    endpoint and the health response. It is evaluated on every request so a
    configuration reload cannot leave a stale bypass behind.
    """
    if settings is None:
        return "proxy_not_initialized"
    if settings.WHATSAPP_PROVIDER != "waha":
        return "provider_disabled"
    if not settings.WAHA_ENABLE_LIVE:
        return "kill_switch_disabled"
    if not settings.WAHA_BASE_URL or not settings.WAHA_API_KEY:
        return "waha_not_configured"
    return None


def live_send_block_detail(reason: str) -> str:
    """Safe, actionable message for a blocked live-send attempt."""
    return {
        "proxy_not_initialized": "Proxy settings are not initialized. No message was sent.",
        "provider_disabled": (
            "WhatsApp dispatch is disabled on the server (WHATSAPP_PROVIDER is not 'waha'). "
            "No message was sent."
        ),
        "kill_switch_disabled": (
            "Live WhatsApp sending is disabled by the server kill switch "
            "(WAHA_ENABLE_LIVE=false). No message was sent."
        ),
        "waha_not_configured": (
            "WAHA is not configured with both WAHA_BASE_URL and WAHA_API_KEY. "
            "No message was sent."
        ),
    }.get(reason, "Live WhatsApp sending is blocked by server policy. No message was sent.")


# --- Middlewares ---

@app.middleware("http")
async def security_and_limit_middleware(request: Request, call_next):
    """Enforces request body size limits and safe logging."""
    # Enforce request body size limit
    content_length = request.headers.get("content-length")
    max_bytes = settings.MAX_REQUEST_BODY_BYTES if settings else 65536
    if content_length and int(content_length) > max_bytes:
        return JSONResponse(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            content={"detail": f"Request body exceeds limit of {max_bytes} bytes."},
        )

    response = await call_next(request)

    # Sanitize logs: never log Authorization headers, query strings with tokens, or request payloads
    logger.info(
        "HTTP %s %s -> %d",
        request.method,
        request.url.path,
        response.status_code,
    )
    return response


# --- Endpoints ---

@app.get("/api/health")
async def health_check(authorization: Optional[str] = Header(None)) -> Dict[str, Any]:
    """
    Contract verification probe for Android client and container health checks.
    
    Distinguishes:
    - Proxy process is running (HTTP 200).
    - Android authentication validity (validates JWT if Authorization header is provided; returns 401 if invalid/expired).
    - WAHA connectivity and session status (actively checked via the upstream provider probe).

    The ``waakg`` key is retained as a deprecated compatibility alias for older
    Android clients. New clients should read ``waha``.
    """
    token = extract_bearer_token(authorization)
    auth_status: Dict[str, Any]

    if token:
        # Validate device JWT if supplied
        try:
            payload = verify_device_jwt(token, settings.JWT_SECRET, settings.JWT_ALGORITHM)
            auth_status = {
                "authenticated": True,
                "deviceId": payload.get("sub"),
                "tokenExpiresAt": payload.get("exp"),
            }
        except HTTPException:
            # Re-raise 401 for Android client contract verification
            raise
    else:
        auth_status = {
            "authenticated": False,
            "deviceId": None,
            "note": "No Authorization header provided. Unauthenticated health probe.",
        }

    # Query the active WhatsApp provider status. This performs GET probes only;
    # it never calls WAHA's send endpoint.
    raw_whatsapp_status = await upstream_provider.check_health()
    whatsapp_status: Dict[str, Any] = dict(raw_whatsapp_status or {})
    block_reason = live_send_block_reason()
    session_live = bool(whatsapp_status.get("live_enabled"))
    whatsapp_status["session_live_enabled"] = session_live
    whatsapp_status["live_send_enabled"] = block_reason is None
    # live_enabled means both the session is ready and the kill switch is open.
    whatsapp_status["live_enabled"] = session_live and block_reason is None
    if block_reason is None:
        whatsapp_status.pop("live_send_blocked_reason", None)
        whatsapp_status.pop("live_send_blocked_detail", None)
    else:
        whatsapp_status["live_send_blocked_reason"] = block_reason
        whatsapp_status["live_send_blocked_detail"] = live_send_block_detail(block_reason)

    return {
        "status": "UP",
        "proxy": "running",
        "version": "1.0.0",
        "provider": settings.WHATSAPP_PROVIDER,
        "live_send_enabled": block_reason is None,
        "live_send_blocked_reason": block_reason,
        "auth": auth_status,
        "waha": whatsapp_status,
        # Deprecated compatibility alias for existing Android clients.
        "waakg": whatsapp_status,
    }


@app.post("/api/message", response_model=MessageResponse)
async def dispatch_message(
    payload: MessageRequest,
    response: Response,
    authorization: Optional[str] = Header(None),
    x_idempotency_key: Optional[str] = Header(None),
) -> MessageResponse:
    """
    Authenticated message dispatch endpoint matching BackendProxyClient.kt contract.

    Safety order:
    1. Verify the device JWT.
    2. Require an explicit, bounded X-Idempotency-Key that matches jobId.
    3. Enforce the per-device rate limit.
    4. Enforce the server live-send kill switch. When blocked, no provider
       method is called and no WAHA send request is made.
    5. Require explicit consent and reject opted-out recipients.
    6. Replay a completed idempotency record or claim the key before dispatch.
    """
    # Guard 1: Verify JWT Bearer token
    token = extract_bearer_token(authorization)
    if not token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing Authorization: Bearer <token> header.",
        )

    claims = verify_device_jwt(token, settings.JWT_SECRET, settings.JWT_ALGORITHM)
    device_id = claims["sub"]

    # Guard 2: A client must provide an explicit idempotency key. Never
    # silently substitute jobId: that previously allowed a caller that omitted
    # the header to look idempotent without proving it understood the contract.
    provided_key = x_idempotency_key.strip() if x_idempotency_key else ""
    if not provided_key:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=(
                "Missing required X-Idempotency-Key header. "
                "The backend will not substitute jobId automatically."
            ),
            headers={"X-Delivery-Outcome": "not_sent"},
        )
    if not IDEMPOTENCY_KEY_PATTERN.fullmatch(provided_key):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=(
                "Invalid X-Idempotency-Key. Use 6-200 characters from "
                "A-Z, a-z, 0-9, '.', '_', ':' or '-'."
            ),
            headers={"X-Delivery-Outcome": "not_sent"},
        )
    if provided_key != payload.jobId:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="X-Idempotency-Key must match the request jobId.",
            headers={"X-Delivery-Outcome": "not_sent"},
        )
    idempotency_key = provided_key

    # Guard 3: Enforce rate limiting per device
    is_allowed = await rate_limiter.is_allowed(device_id)
    if not is_allowed:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Backend proxy rate limit exceeded. Please back off.",
            headers={"X-Delivery-Outcome": "not_sent"},
        )

    # Guard 4: The server-side live-send kill switch. This check happens before
    # any idempotency claim or provider call, so a blocked request cannot reach
    # WAHA POST /api/sendText under any provider configuration.
    block_reason = live_send_block_reason()
    if block_reason is not None:
        logger.warning(
            "Blocked live dispatch for job %s by server gate (%s)",
            idempotency_key,
            block_reason,
        )
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=live_send_block_detail(block_reason),
            headers={
                "X-Delivery-Outcome": "not_sent",
                "X-Live-Send-Enabled": "false",
            },
        )

    # Guard 5: Consent and opt-out eligibility. Both fields are explicit; a
    # missing consent field defaults to false and therefore blocks dispatch.
    if payload.optOut:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=(
                "Recipient has opted out of WhatsApp messaging. "
                "No message was sent."
            ),
            headers={"X-Delivery-Outcome": "not_sent"},
        )
    if not payload.consent:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=(
                "Recipient WhatsApp consent is not recorded. "
                "Record explicit consent before dispatching. No message was sent."
            ),
            headers={"X-Delivery-Outcome": "not_sent"},
        )

    # Guard 6: Replay a completed record or atomically claim the key.
    cached = await idempotency_store.get(idempotency_key)
    if cached is not None:
        cached_status, cached_body = cached
        if cached_status in (200, 201):
            logger.info("Idempotent replay for job %s", idempotency_key)
            response.headers["X-Delivery-Outcome"] = "accepted"
            response.headers["X-Live-Send-Enabled"] = "true"
            return MessageResponse(**cached_body)

        # A previous attempt ended with an unknown delivery outcome. Replaying
        # it blindly could create a duplicate WhatsApp message.
        cached_detail = cached_body.get("detail") or (
            "A previous attempt for this job did not produce a confirmed delivery. "
            "Verify in WhatsApp before retrying with a new jobId."
        )
        replay_outcome = "unknown" if cached_body.get("duplicateRisk") else "not_sent"
        raise HTTPException(
            status_code=cached_status,
            detail=cached_detail,
            headers={"X-Delivery-Outcome": replay_outcome},
        )

    started = await idempotency_store.start_inflight(idempotency_key)
    if not started:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Job {idempotency_key} is already in-flight or completed.",
            headers={"X-Delivery-Outcome": "not_sent"},
        )

    try:
        # Guard 7: Forward to the configured upstream provider.
        result = await upstream_provider.send_message(
            session_id=payload.sessionId,
            jid=payload.jid,
            content=payload.content,
            job_id=payload.jobId,
            template_name=payload.templateName,
        )

        if not result.success:
            if result.duplicate_risk:
                # The request may have reached WhatsApp. Lock the idempotency
                # key so an automatic retry cannot silently send it twice.
                failure_status = 504 if result.status == "timeout" else 502
                failure_body = {
                    "detail": result.error or (
                        "Delivery outcome is unknown. Verify in WhatsApp before retrying."
                    ),
                    "status": result.status or "unknown",
                    "duplicateRisk": True,
                }
                await idempotency_store.complete(idempotency_key, failure_status, failure_body)
                logger.warning(
                    "Dispatch outcome unknown for job %s (category=%s); idempotency key locked.",
                    idempotency_key,
                    result.status,
                )
                raise HTTPException(
                    status_code=failure_status,
                    detail=failure_body["detail"],
                    headers={"X-Delivery-Outcome": "unknown"},
                )

            await idempotency_store.abort(idempotency_key)
            raise HTTPException(
                status_code=result.status_code,
                detail=result.error or f"Upstream rejected message with status {result.status_code}",
                headers={"X-Delivery-Outcome": "not_sent"},
            )

        response_data = {
            "success": True,
            "messageId": result.message_id,
            "status": result.status,
        }

        # Cache successful response for future retries.
        await idempotency_store.complete(idempotency_key, 200, response_data)
        response.headers["X-Delivery-Outcome"] = "accepted"
        response.headers["X-Live-Send-Enabled"] = "true"

        return MessageResponse(**response_data)

    except HTTPException:
        # A completed unknown record is not in-flight, so abort is a no-op and
        # keeps the lock. Definitive rejections abort and may be retried safely.
        await idempotency_store.abort(idempotency_key)
        raise
    except Exception as err:
        # The provider may have reached WAHA before this exception. Treat the
        # outcome as unknown and lock the key instead of allowing a silent
        # duplicate on automatic retry.
        failure_body = {
            "detail": (
                "Unexpected proxy error after a dispatch attempt. Delivery outcome "
                "is unknown. Verify in WhatsApp before retrying with a new jobId."
            ),
            "status": "unknown",
            "duplicateRisk": True,
        }
        await idempotency_store.complete(idempotency_key, 500, failure_body)
        logger.error(
            "Unhandled error after dispatch attempt for job %s (%s); "
            "delivery outcome unknown and idempotency key locked.",
            idempotency_key,
            type(err).__name__,
        )
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=failure_body["detail"],
            headers={"X-Delivery-Outcome": "unknown"},
        )
