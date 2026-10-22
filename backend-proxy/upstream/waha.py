"""
WAHA upstream adapter for the WhatsApp Backend Proxy.

Verified against the official WAHA OpenAPI document version 2026.8.2 and the
WAHA 2026.9.2 images:

- POST /api/sendText
  Header: X-Api-Key
  Body:   {"session": "...", "chatId": "<international digits>@c.us", "text": "..."}
- GET /api/sessions/{session}
  Header: X-Api-Key
- GET /health
  Header: X-Api-Key (unless explicitly excluded by WAHA configuration)

The adapter never logs API keys, recipient numbers, or message bodies.
"""

import logging
import os
import re
from typing import Any, Dict, Optional
from urllib.parse import quote

import httpx

from .base import IWhatsAppProvider, UpstreamResult

logger = logging.getLogger("backend-proxy.waha")

SESSION_READY = "WORKING"

def extract_message_id(data: Any) -> Optional[str]:
    """Return a WAHA message ID without inventing one when it is absent.

    WAHA normally returns ``{"id": "<message id>"}`` for ``sendText`` (201), but
    the raw ``_data``/``key`` shapes vary between engines and releases. Keep the
    extraction strict: if no real ID is present, return ``None`` rather than a
    synthetic acknowledgement.
    """
    if not isinstance(data, dict):
        return None

    candidates = [data.get("id"), data.get("messageId")]
    nested = data.get("_data")
    if isinstance(nested, dict):
        candidates.append(nested.get("id"))
        nested_key = nested.get("key")
        if isinstance(nested_key, dict):
            candidates.append(nested_key.get("id"))

    for candidate in candidates:
        if isinstance(candidate, str) and candidate.strip():
            return candidate.strip()
        if isinstance(candidate, dict):
            for key in ("id", "_serialized"):
                value = candidate.get(key)
                if isinstance(value, str) and value.strip():
                    return value.strip()
            key = candidate.get("key")
            if isinstance(key, dict):
                value = key.get("id")
                if isinstance(value, str) and value.strip():
                    return value.strip()
    return None


_SESSION_STATUS_MESSAGES = {
    "WORKING": "WAHA is reachable and the WhatsApp session is connected.",
    "SCAN_QR_CODE": (
        "WAHA is reachable, but the WhatsApp session needs pairing. "
        "Open the WAHA dashboard and scan the QR code with the dedicated test phone."
    ),
    "STARTING": "WAHA is reachable; the WhatsApp session is starting.",
    "STOPPED": "WAHA is reachable, but the WhatsApp session is stopped. Start it in the WAHA dashboard.",
    "FAILED": "WAHA is reachable, but the WhatsApp session failed. Check the WAHA logs and restart it.",
    "PASSKEY_REQUIRED": "WAHA is reachable, but WhatsApp requires a passkey to complete pairing.",
    "PASSKEY_CONFIRMATION_REQUIRED": (
        "WAHA is reachable, but WhatsApp passkey confirmation is required to complete pairing."
    ),
}


class WahaProvider(IWhatsAppProvider):
    """Adapter for a self-hosted WAHA gateway.

    The WAHA session is selected by server-side configuration only. The
    Android-supplied sessionId is retained for the existing API contract but is
    never used to select an arbitrary gateway session.
    """

    def __init__(
        self,
        base_url: str,
        api_key: str,
        session: str = "default",
        timeout_seconds: float = 10.0,
        verify_session: bool = True,
        transport: Optional[httpx.AsyncBaseTransport] = None,
    ):
        self.base_url = base_url.strip().rstrip("/")
        self.api_key = api_key.strip()
        self.session = session.strip()
        self.timeout_seconds = timeout_seconds
        self.verify_session = verify_session
        self._transport = transport

        # Test safety: the suite must never be able to reach a real WAHA
        # container just because a developer has live credentials in .env.
        # Production calls do not have PYTEST_CURRENT_TEST set. Every provider
        # test must supply httpx.MockTransport explicitly.
        if self._transport is None and os.environ.get("PYTEST_CURRENT_TEST"):
            raise RuntimeError(
                "Real WAHA network access is blocked during tests. "
                "Pass httpx.MockTransport to WahaProvider."
            )

    # ------------------------------------------------------------------
    # Request helpers
    # ------------------------------------------------------------------

    @property
    def _headers(self) -> Dict[str, str]:
        return {
            "X-Api-Key": self.api_key,
            "Accept": "application/json",
            "Content-Type": "application/json",
        }

    def _client(self, timeout_seconds: Optional[float] = None) -> httpx.AsyncClient:
        effective = timeout_seconds if timeout_seconds is not None else self.timeout_seconds
        return httpx.AsyncClient(
            timeout=httpx.Timeout(effective, connect=min(5.0, effective)),
            transport=self._transport,
            follow_redirects=False,
        )

    @property
    def _session_url(self) -> str:
        return f"{self.base_url}/api/sessions/{quote(self.session, safe='')}"

    # ------------------------------------------------------------------
    # Message normalization
    # ------------------------------------------------------------------

    @staticmethod
    def normalize_chat_id(jid: str) -> str:
        """Convert an Android JID or raw phone number to WAHA's chatId format.

        Android currently sends ``<digits>@s.whatsapp.net``. WAHA requires
        ``<digits>@c.us``. Raw international numbers are also accepted.
        """
        raw = (jid or "").strip()
        if not raw:
            raise ValueError("recipient is empty")

        if "@" in raw:
            local, _, domain = raw.partition("@")
            if domain.strip().lower() not in ("s.whatsapp.net", "c.us"):
                raise ValueError("only individual WhatsApp recipients are supported")
            digits = local
        else:
            digits = raw

        digits = re.sub(r"[\s\-().+]", "", digits)
        if digits.startswith("00"):
            digits = digits[2:]

        if not digits.isdigit():
            raise ValueError("recipient must contain digits only")
        if len(digits) < 7 or len(digits) > 15:
            raise ValueError("recipient must include a country code (7-15 digits)")
        if digits.startswith("0"):
            raise ValueError("recipient must be in international format without a leading zero")

        return f"{digits}@c.us"

    # ------------------------------------------------------------------
    # Session preflight
    # ------------------------------------------------------------------

    async def _check_session_ready(self) -> Dict[str, Any]:
        """Return a safe, structured result describing the WAHA session state."""
        try:
            async with self._client(timeout_seconds=min(5.0, self.timeout_seconds)) as client:
                response = await client.get(self._session_url, headers=self._headers)
        except httpx.ConnectTimeout:
            return {
                "ok": False,
                "status_code": 504,
                "status": "timeout",
                "error": "Timed out connecting to WAHA while checking the session. No message was sent.",
            }
        except httpx.TimeoutException:
            return {
                "ok": False,
                "status_code": 504,
                "status": "timeout",
                "error": "Timed out checking the WAHA session status. No message was sent.",
            }
        except httpx.ConnectError:
            return {
                "ok": False,
                "status_code": 502,
                "status": "unreachable",
                "error": "Cannot reach WAHA at the configured local endpoint. No message was sent.",
            }
        except httpx.TransportError:
            return {
                "ok": False,
                "status_code": 502,
                "status": "unreachable",
                "error": "Network error while checking the WAHA session. No message was sent.",
            }
        except Exception as err:  # pragma: no cover - defensive
            logger.warning("WAHA session probe failed: %s", type(err).__name__)
            return {
                "ok": False,
                "status_code": 502,
                "status": "unreachable",
                "error": "Could not check the WAHA session. No message was sent.",
            }

        http_status = response.status_code
        if http_status in (401, 403):
            return {
                "ok": False,
                "status_code": 502,
                "status": "unauthorized",
                "http_status": http_status,
                "error": "WAHA rejected the backend API key. Check WAHA_API_KEY on the backend.",
            }
        if http_status == 404:
            return {
                "ok": False,
                "status_code": 503,
                "status": "session_not_found",
                "http_status": http_status,
                "error": (
                    f"WAHA session '{self.session}' does not exist. "
                    "Create and pair it in the WAHA dashboard before sending messages."
                ),
            }
        if http_status < 200 or http_status >= 300:
            return {
                "ok": False,
                "status_code": 502,
                "status": "provider_error",
                "http_status": http_status,
                "error": f"WAHA returned HTTP {http_status} while checking the session. No message was sent.",
            }

        try:
            data = response.json()
        except Exception:
            data = {}

        session_status = data.get("status") if isinstance(data, dict) else None
        if not isinstance(session_status, str) or not session_status:
            return {
                "ok": False,
                "status_code": 502,
                "status": "degraded",
                "http_status": http_status,
                "error": "WAHA returned an unreadable session response. No message was sent.",
            }

        if session_status != SESSION_READY:
            message = _SESSION_STATUS_MESSAGES.get(
                session_status,
                f"WhatsApp session '{self.session}' is not connected (status: {session_status}).",
            )
            return {
                "ok": False,
                "status_code": 503,
                "status": "session_not_connected",
                "http_status": http_status,
                "session_status": session_status,
                "error": message,
            }

        return {
            "ok": True,
            "status_code": 200,
            "status": "connected",
            "http_status": http_status,
            "session_status": session_status,
        }

    # ------------------------------------------------------------------
    # IWhatsAppProvider implementation
    # ------------------------------------------------------------------

    async def send_message(
        self,
        session_id: str,
        jid: str,
        content: str,
        job_id: str,
        template_name: str,
    ) -> UpstreamResult:
        try:
            chat_id = self.normalize_chat_id(jid)
        except ValueError as err:
            return UpstreamResult(
                success=False,
                status_code=400,
                error=(
                    f"Invalid WhatsApp recipient: {err}. Use an international number with "
                    "the country code, for example 919876543210@c.us."
                ),
                status="invalid_recipient",
            )

        if self.verify_session:
            session_check = await self._check_session_ready()
            if not session_check["ok"]:
                logger.warning(
                    "WAHA preflight blocked job=%s category=%s http_status=%s",
                    job_id,
                    session_check.get("status"),
                    session_check.get("http_status"),
                )
                return UpstreamResult(
                    success=False,
                    status_code=session_check["status_code"],
                    error=session_check["error"],
                    status=session_check["status"],
                )

        endpoint = f"{self.base_url}/api/sendText"
        body = {
            "session": self.session,
            "chatId": chat_id,
            "text": content,
        }

        try:
            async with self._client() as client:
                response = await client.post(endpoint, headers=self._headers, json=body)
        except httpx.ConnectTimeout:
            return UpstreamResult(
                success=False,
                status_code=504,
                error="Timed out connecting to WAHA. No message was sent.",
                status="timeout",
            )
        except httpx.TimeoutException:
            logger.warning("WAHA send timed out for job=%s; delivery outcome unknown", job_id)
            return UpstreamResult(
                success=False,
                status_code=504,
                error=(
                    "WAHA did not respond in time. The message may or may not have been sent. "
                    "Do not retry automatically; verify in WhatsApp first."
                ),
                status="timeout",
                duplicate_risk=True,
            )
        except httpx.ConnectError:
            return UpstreamResult(
                success=False,
                status_code=502,
                error="Cannot connect to WAHA. No message was sent.",
                status="unreachable",
            )
        except httpx.TransportError as err:
            logger.warning("WAHA transport error for job=%s: %s", job_id, type(err).__name__)
            return UpstreamResult(
                success=False,
                status_code=502,
                error=(
                    "Network error after contacting WAHA. The message may or may not have been sent. "
                    "Do not retry automatically; verify in WhatsApp first."
                ),
                status="unknown",
                duplicate_risk=True,
            )
        except Exception as err:  # pragma: no cover - defensive
            logger.error("Unexpected WAHA send error for job=%s: %s", job_id, type(err).__name__)
            return UpstreamResult(
                success=False,
                status_code=500,
                error=(
                    "Unexpected error while sending through WAHA. Delivery outcome is unknown. "
                    "Do not retry automatically; verify in WhatsApp first."
                ),
                status="unknown",
                duplicate_risk=True,
            )

        http_status = response.status_code

        if http_status in (200, 201):
            data: Dict[str, Any] = {}
            try:
                parsed = response.json()
                if isinstance(parsed, dict):
                    data = parsed
            except Exception:
                data = {}

            message_id = extract_message_id(data)

            return UpstreamResult(
                success=True,
                status_code=http_status,
                message_id=message_id,
                status="sent",
                raw_response={"id": message_id, "ack": data.get("ack")},
            )

        if http_status in (400, 404, 409, 422):
            logger.warning("WAHA rejected message job=%s http_status=%s category=invalid_recipient", job_id, http_status)
            return UpstreamResult(
                success=False,
                status_code=400,
                error=(
                    "WAHA rejected the recipient or message. Verify the number is in international "
                    "format, includes the country code, and is registered on WhatsApp."
                ),
                status="invalid_recipient",
            )

        if http_status in (401, 403):
            logger.warning("WAHA authentication failed job=%s http_status=%s", job_id, http_status)
            return UpstreamResult(
                success=False,
                status_code=502,
                error="WAHA rejected the backend API key. Check WAHA_API_KEY on the backend.",
                status="unauthorized",
            )

        if http_status == 429:
            logger.warning("WAHA rate limited message job=%s", job_id)
            return UpstreamResult(
                success=False,
                status_code=429,
                error="WAHA rate limit reached. Wait before retrying; the message was not sent.",
                status="rate_limited",
            )

        if http_status >= 500:
            logger.warning("WAHA server error job=%s http_status=%s; delivery outcome unknown", job_id, http_status)
            return UpstreamResult(
                success=False,
                status_code=502,
                error=(
                    f"WAHA returned HTTP {http_status}. The message may or may not have been sent. "
                    "Do not retry automatically; verify in WhatsApp first."
                ),
                status="unknown",
                duplicate_risk=True,
            )

        logger.warning("WAHA unexpected rejection job=%s http_status=%s", job_id, http_status)
        return UpstreamResult(
            success=False,
            status_code=502,
            error=f"WAHA rejected the request (HTTP {http_status}).",
            status="provider_rejection",
        )

    async def check_health(self) -> Dict[str, Any]:
        """Probe WAHA server health and the configured session state."""
        result: Dict[str, Any] = {
            "configured": True,
            "provider": "waha",
            "status": "degraded",
            "live_enabled": False,
            "endpoint": self.base_url,
            "session": self.session,
        }

        try:
            async with self._client(timeout_seconds=min(3.0, self.timeout_seconds)) as client:
                health_response = await client.get(f"{self.base_url}/health", headers=self._headers)
                result["health_http_status"] = health_response.status_code

                if health_response.status_code in (401, 403):
                    result["status"] = "unauthorized"
                    result["message"] = "WAHA rejected the backend API key. Check WAHA_API_KEY."
                    return result
                if health_response.status_code != 200:
                    result["status"] = "degraded"
                    result["message"] = (
                        f"WAHA health endpoint returned HTTP {health_response.status_code}."
                    )
                    return result

                result["health_http_status"] = health_response.status_code
                session_response = await client.get(self._session_url, headers=self._headers)
        except httpx.ConnectTimeout:
            result["status"] = "unreachable"
            result["error"] = "timeout"
            result["message"] = "Timed out connecting to WAHA."
            return result
        except httpx.TimeoutException:
            result["status"] = "unreachable"
            result["error"] = "timeout"
            result["message"] = "Timed out waiting for WAHA."
            return result
        except httpx.ConnectError:
            result["status"] = "unreachable"
            result["error"] = "connection_refused"
            result["message"] = "Cannot reach WAHA at the configured local endpoint."
            return result
        except httpx.TransportError as err:
            result["status"] = "unreachable"
            result["error"] = type(err).__name__
            result["message"] = "Network error while contacting WAHA."
            return result
        except Exception as err:  # pragma: no cover - defensive
            result["status"] = "unreachable"
            result["error"] = type(err).__name__
            result["message"] = "Could not contact WAHA."
            return result

        session_http = session_response.status_code
        result["session_http_status"] = session_http

        if session_http in (401, 403):
            result["status"] = "unauthorized"
            result["message"] = "WAHA rejected the backend API key. Check WAHA_API_KEY."
            return result
        if session_http == 404:
            result["status"] = "session_not_found"
            result["message"] = (
                f"WAHA session '{self.session}' does not exist. Create and pair it in the WAHA dashboard."
            )
            return result
        if session_http != 200:
            result["status"] = "degraded"
            result["message"] = f"WAHA session endpoint returned HTTP {session_http}."
            return result

        try:
            data = session_response.json()
        except Exception:
            data = {}

        session_status = data.get("status") if isinstance(data, dict) else None
        result["session_status"] = session_status

        if not isinstance(session_status, str) or not session_status:
            result["status"] = "degraded"
            result["message"] = "WAHA returned an unreadable session response."
            return result

        status_map = {
            "WORKING": "connected",
            "SCAN_QR_CODE": "scan_qr",
            "STARTING": "starting",
            "STOPPED": "stopped",
            "FAILED": "failed",
            "PASSKEY_REQUIRED": "pairing_required",
            "PASSKEY_CONFIRMATION_REQUIRED": "pairing_required",
        }
        result["status"] = status_map.get(session_status, "degraded")
        result["live_enabled"] = session_status == SESSION_READY
        result["message"] = _SESSION_STATUS_MESSAGES.get(
            session_status,
            f"WhatsApp session '{self.session}' status is {session_status}.",
        )
        return result
