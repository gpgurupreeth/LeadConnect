"""
Base interfaces and data transfer objects for WhatsApp upstream providers.
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional, Dict, Any


@dataclass
class UpstreamResult:
    """Result returned by upstream WhatsApp provider.

    duplicate_risk is True only when the request may have reached WhatsApp but
    the provider did not return a definitive answer (for example a timeout or a
    provider 5xx). The proxy caches such outcomes so an automatic retry cannot
    silently send the same message twice.
    """
    success: bool
    status_code: int
    message_id: Optional[str] = None
    error: Optional[str] = None
    status: str = "failed"
    raw_response: Optional[Dict[str, Any]] = None
    duplicate_risk: bool = False


class IWhatsAppProvider(ABC):
    """Abstract interface defining the upstream WhatsApp messaging integration."""

    @abstractmethod
    async def send_message(
        self,
        session_id: str,
        jid: str,
        content: str,
        job_id: str,
        template_name: str,
    ) -> UpstreamResult:
        """Forward message dispatch request to upstream WhatsApp gateway."""
        pass

    @abstractmethod
    async def check_health(self) -> Dict[str, Any]:
        """Perform an active connectivity and authentication probe to upstream provider."""
        pass
