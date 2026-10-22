"""
Tests for GET /api/health endpoint.
Verifies contract verification behavior expected by Android BackendProxyClient.kt.
"""

import pytest


@pytest.mark.asyncio
async def test_health_without_auth(client):
    response = await client.get("/api/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert data["proxy"] == "running"
    assert data["auth"]["authenticated"] is False
    assert data["live_send_enabled"] is False
    assert data["waakg"]["status"] == "unconfigured"
    assert data["waakg"]["configured"] is False
    assert data["waakg"]["live_enabled"] is False


@pytest.mark.asyncio
async def test_health_with_valid_token(client, valid_token):
    response = await client.get(
        "/api/health",
        headers={"Authorization": f"Bearer {valid_token}"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert data["auth"]["authenticated"] is True
    assert data["auth"]["deviceId"] == "test-device-001"


@pytest.mark.asyncio
async def test_health_with_expired_token(client, expired_token):
    response = await client.get(
        "/api/health",
        headers={"Authorization": f"Bearer {expired_token}"},
    )
    assert response.status_code == 401
    data = response.json()
    assert "expired" in data["detail"].lower()


@pytest.mark.asyncio
async def test_health_with_invalid_token(client):
    response = await client.get(
        "/api/health",
        headers={"Authorization": "Bearer invalid.token.payload"},
    )
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_health_reports_waakg_active_when_probed(client, valid_token, mock_live_provider):
    response = await client.get(
        "/api/health",
        headers={"Authorization": f"Bearer {valid_token}"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["waakg"]["status"] == "connected"
    assert data["waakg"]["configured"] is True
    assert data["live_send_enabled"] is True
    assert data["waakg"]["live_enabled"] is True


@pytest.mark.asyncio
async def test_health_reports_kill_switch_blocked_while_session_is_connected(
    client, valid_token, provider_swap
):
    import main as app_module
    from test_waha import Recorder, make_provider

    recorder = Recorder(session_status="WORKING")
    provider_swap(make_provider(recorder))
    app_module.settings.WAHA_ENABLE_LIVE = False

    response = await client.get(
        "/api/health",
        headers={"Authorization": f"Bearer {valid_token}"},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["live_send_enabled"] is False
    assert data["waha"]["session_live_enabled"] is True
    assert data["waha"]["live_enabled"] is False
    assert data["waha"]["live_send_blocked_reason"] == "kill_switch_disabled"
    assert recorder.post_count == 0
