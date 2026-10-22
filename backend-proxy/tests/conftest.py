"""
Pytest configuration and shared fixtures for backend-proxy tests.
"""

import os
import sys
from pathlib import Path
import pytest
from httpx import AsyncClient, ASGITransport

# Ensure backend-proxy root and the test directory are on sys.path
sys.path.insert(0, str(Path(__file__).parent.parent))
sys.path.insert(0, str(Path(__file__).parent))

# Set strong test environment variables before importing app
TEST_JWT_SECRET = "a9f8b7c6d5e4a3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8"
os.environ["JWT_SECRET"] = TEST_JWT_SECRET
os.environ["PORT"] = "8081"
os.environ["WHATSAPP_PROVIDER"] = "disabled"
os.environ["WAHA_ENABLE_LIVE"] = "false"
os.environ["WAAKG_ENABLE_LIVE"] = "false"
os.environ["RATE_LIMIT_PER_MINUTE"] = "100"

from main import app
from auth import create_device_jwt
import main as app_module
from upstream.base import IWhatsAppProvider, UpstreamResult


class MockUpstreamProvider(IWhatsAppProvider):
    """Mock provider for simulating live WAHA behavior in unit tests."""

    def __init__(self, should_succeed: bool = True, return_message_id: str = "wa_mock_msg_12345"):
        self.should_succeed = should_succeed
        self.return_message_id = return_message_id
        self.calls = []

    async def send_message(
        self,
        session_id: str,
        jid: str,
        content: str,
        job_id: str,
        template_name: str,
    ) -> UpstreamResult:
        self.calls.append({
            "sessionId": session_id,
            "jid": jid,
            "content": content,
            "jobId": job_id,
            "templateName": template_name,
        })
        if self.should_succeed:
            return UpstreamResult(
                success=True,
                status_code=200,
                message_id=self.return_message_id,
                status="sent",
            )
        else:
            return UpstreamResult(
                success=False,
                status_code=502,
                error="Downstream WAHA server unreachable",
                status="unreachable",
            )

    async def check_health(self):
        return {
            "configured": True,
            "status": "connected" if self.should_succeed else "unreachable",
            "live_enabled": True,
        }


@pytest.fixture
def jwt_secret():
    return TEST_JWT_SECRET


@pytest.fixture
def valid_token(jwt_secret):
    return create_device_jwt(device_id="test-device-001", secret=jwt_secret, expires_in_minutes=5)


@pytest.fixture
def expired_token(jwt_secret):
    return create_device_jwt(device_id="test-device-001", secret=jwt_secret, expires_in_minutes=-1)


import pytest_asyncio

@pytest_asyncio.fixture
async def client():
    app_module.init_app_state()
    # Every test starts with live sending disabled unless it explicitly opts in
    # with mock_live_provider/provider_swap. The mock fixtures never create a
    # real WahaProvider and cannot reach a live WAHA container.
    app_module.settings.WHATSAPP_PROVIDER = "disabled"
    app_module.settings.WAHA_ENABLE_LIVE = False
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac


def _enable_test_dispatch_gate():
    """Open the dispatch gate for mocked providers only."""
    app_module.settings.WHATSAPP_PROVIDER = "waha"
    app_module.settings.WAHA_ENABLE_LIVE = True
    app_module.settings.WAHA_BASE_URL = "http://waha.test"
    app_module.settings.WAHA_API_KEY = "test-only-waha-key"


@pytest.fixture
def mock_live_provider(client):
    original_provider = app_module.upstream_provider
    original_gate = (
        app_module.settings.WHATSAPP_PROVIDER,
        app_module.settings.WAHA_ENABLE_LIVE,
        app_module.settings.WAHA_BASE_URL,
        app_module.settings.WAHA_API_KEY,
    )
    _enable_test_dispatch_gate()
    mock = MockUpstreamProvider(should_succeed=True)
    app_module.upstream_provider = mock
    yield mock
    app_module.upstream_provider = original_provider
    (
        app_module.settings.WHATSAPP_PROVIDER,
        app_module.settings.WAHA_ENABLE_LIVE,
        app_module.settings.WAHA_BASE_URL,
        app_module.settings.WAHA_API_KEY,
    ) = original_gate


@pytest.fixture
def provider_swap(client):
    """Replace the global provider with a mocked transport and enable the gate.

    The gate is enabled only for this fixture's mock provider; no real WAHA
    network path is created here.
    """
    original_provider = app_module.upstream_provider
    original_gate = (
        app_module.settings.WHATSAPP_PROVIDER,
        app_module.settings.WAHA_ENABLE_LIVE,
        app_module.settings.WAHA_BASE_URL,
        app_module.settings.WAHA_API_KEY,
    )
    _enable_test_dispatch_gate()

    def _swap(provider):
        app_module.upstream_provider = provider
        return provider

    yield _swap
    app_module.upstream_provider = original_provider
    (
        app_module.settings.WHATSAPP_PROVIDER,
        app_module.settings.WAHA_ENABLE_LIVE,
        app_module.settings.WAHA_BASE_URL,
        app_module.settings.WAHA_API_KEY,
    ) = original_gate
