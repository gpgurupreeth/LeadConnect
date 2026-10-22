"""
Active upstream adapter for WA-AKG service.
Communicates over HTTPS using the master X-API-Key header.
"""

from typing import Dict, Any, Optional
import httpx
from .base import IWhatsAppProvider, UpstreamResult


class WaaKgProvider(IWhatsAppProvider):
    """
    Adapter communicating with upstream WA-AKG service.
    Implements POST /messages/{sessionId}/{jid}/send contract with master X-API-Key.
    """

    def __init__(self, base_url: str, api_key: str, timeout_seconds: float = 10.0):
        self.base_url = base_url.strip().rstrip("/")
        self.api_key = api_key.strip()
        self.timeout_seconds = timeout_seconds

    async def send_message(
        self,
        session_id: str,
        jid: str,
        content: str,
        job_id: str,
        template_name: str,
    ) -> UpstreamResult:
        endpoint = f"{self.base_url}/messages/{session_id}/{jid}/send"
        headers = {
            "X-API-Key": self.api_key,
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-Idempotency-Key": job_id,
        }
        body = {
            "sessionId": session_id,
            "jid": jid,
            "content": content,
            "jobId": job_id,
            "templateName": template_name,
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
                response = await client.post(endpoint, headers=headers, json=body)

            status_code = response.status_code
            if 200 <= status_code < 300:
                try:
                    data = response.json()
                except Exception:
                    data = {}

                # Extract provider message ID if provided. Never fabricate one.
                message_id = (
                    data.get("messageId")
                    or data.get("id")
                    or data.get("key", {}).get("id")
                )

                return UpstreamResult(
                    success=True,
                    status_code=status_code,
                    message_id=str(message_id) if message_id else None,
                    status="sent",
                    raw_response=data if isinstance(data, dict) else {},
                )
            else:
                error_detail = response.text[:200] if response.text else f"HTTP {status_code}"
                return UpstreamResult(
                    success=False,
                    status_code=status_code,
                    error=f"WA-AKG upstream rejected message ({status_code}): {error_detail}",
                    status="failed",
                )

        except httpx.TimeoutException:
            return UpstreamResult(
                success=False,
                status_code=504,
                error="WA-AKG upstream connection timed out.",
                status="timeout",
            )
        except httpx.ConnectError as err:
            return UpstreamResult(
                success=False,
                status_code=502,
                error=f"Failed to connect to WA-AKG service at {self.base_url}: connection refused.",
                status="unreachable",
            )
        except Exception as err:
            return UpstreamResult(
                success=False,
                status_code=500,
                error=f"Unexpected error communicating with WA-AKG: {type(err).__name__}",
                status="failed",
            )

    async def check_health(self) -> Dict[str, Any]:
        """Probe the upstream WA-AKG service health endpoint."""
        health_url = f"{self.base_url}/health"
        headers = {"X-API-Key": self.api_key}

        try:
            async with httpx.AsyncClient(timeout=3.0) as client:
                response = await client.get(health_url, headers=headers)

            if response.status_code in (200, 204):
                return {
                    "configured": True,
                    "status": "connected",
                    "live_enabled": True,
                    "endpoint": health_url,
                    "http_status": response.status_code,
                }
            else:
                return {
                    "configured": True,
                    "status": "degraded",
                    "live_enabled": True,
                    "endpoint": health_url,
                    "http_status": response.status_code,
                }
        except Exception as err:
            return {
                "configured": True,
                "status": "unreachable",
                "live_enabled": True,
                "endpoint": health_url,
                "error": type(err).__name__,
            }
