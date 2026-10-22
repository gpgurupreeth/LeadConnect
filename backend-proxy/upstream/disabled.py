"""
Disabled/Unconfigured provider implementation.
Safely rejects dispatch requests without pretending a mock message was delivered.
"""

from typing import Dict, Any
from .base import IWhatsAppProvider, UpstreamResult


class DisabledProvider(IWhatsAppProvider):
    """
    Default provider used when no WhatsApp provider is configured or enabled.
    """

    def __init__(self, reason: str = "WhatsApp dispatch is disabled or unconfigured."):
        self.reason = reason

    async def send_message(
        self,
        session_id: str,
        jid: str,
        content: str,
        job_id: str,
        template_name: str,
    ) -> UpstreamResult:
        return UpstreamResult(
            success=False,
            status_code=503,
            error=(
                f"{self.reason} "
                "To enable real dispatch, set WHATSAPP_PROVIDER=waha, WAHA_ENABLE_LIVE=true, "
                "and configure WAHA_BASE_URL, WAHA_API_KEY, and WAHA_SESSION on the backend."
            ),
            status="unconfigured",
        )

    async def check_health(self) -> Dict[str, Any]:
        return {
            "configured": False,
            "provider": "disabled",
            "status": "unconfigured",
            "live_enabled": False,
            "message": self.reason,
        }
