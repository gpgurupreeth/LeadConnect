"""
Tests for POST /api/message endpoint.
Verifies payload validation, response formatting, error mapping, and Android client compatibility.
"""

import pytest


@pytest.mark.asyncio
async def test_message_missing_token_returns_401(client):
    payload = {
        "sessionId": "default",
        "jid": "1234567890@s.whatsapp.net",
        "content": "Hello customer",
        "jobId": "job-100",
        "templateName": "follow_up",
    }
    response = await client.post("/api/message", json=payload)
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_message_invalid_token_returns_401(client):
    payload = {
        "sessionId": "default",
        "jid": "1234567890@s.whatsapp.net",
        "content": "Hello customer",
        "jobId": "job-100",
        "templateName": "follow_up",
    }
    response = await client.post(
        "/api/message",
        json=payload,
        headers={"Authorization": "Bearer bad.token"},
    )
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_message_invalid_payload_validation(client, valid_token):
    # Missing jid and content
    invalid_payload = {
        "sessionId": "default",
        "jobId": "job-100",
    }
    response = await client.post(
        "/api/message",
        json=invalid_payload,
        headers={"Authorization": f"Bearer {valid_token}"},
    )
    assert response.status_code == 422


@pytest.mark.asyncio
async def test_message_unconfigured_upstream_returns_503(client, valid_token):
    payload = {
        "sessionId": "default",
        "jid": "919876543210@s.whatsapp.net",
        "content": "Follow-up message",
        "jobId": "job-unconfigured-1",
        "templateName": "intro",
        "consent": True,
        "optOut": False,
    }
    response = await client.post(
        "/api/message",
        json=payload,
        headers={
            "Authorization": f"Bearer {valid_token}",
            "X-Idempotency-Key": "job-unconfigured-1",
        },
    )
    # Must fail with 503 and explain live dispatch is disabled, NOT fake success
    assert response.status_code == 503
    assert response.headers["x-delivery-outcome"] == "not_sent"
    data = response.json()
    assert "disabled" in data["detail"].lower()


@pytest.mark.asyncio
async def test_message_success_matches_android_parser(client, valid_token, mock_live_provider):
    payload = {
        "sessionId": "default",
        "jid": "919876543210@s.whatsapp.net",
        "content": "Automated follow-up message for template: follow_up_intro",
        "jobId": "job-success-200",
        "templateName": "follow_up_intro",
        "consent": True,
        "optOut": False,
    }
    response = await client.post(
        "/api/message",
        json=payload,
        headers={
            "Authorization": f"Bearer {valid_token}",
            "X-Idempotency-Key": "job-success-200",
        },
    )
    assert response.status_code == 200
    data = response.json()

    # Crucial Android compatibility assertions:
    # Android BackendProxyClient does:
    # val messageId = responseJson?.optString("messageId")
    assert "messageId" in data
    assert data["messageId"] == "wa_mock_msg_12345"
    assert data["success"] is True
    assert data["status"] == "sent"


@pytest.mark.asyncio
async def test_message_downstream_error_mapped(client, valid_token, mock_live_provider):
    mock_live_provider.should_succeed = False
    payload = {
        "sessionId": "default",
        "jid": "919876543210@s.whatsapp.net",
        "content": "Automated message",
        "jobId": "job-fail-502",
        "templateName": "intro",
        "consent": True,
        "optOut": False,
    }
    response = await client.post(
        "/api/message",
        json=payload,
        headers={
            "Authorization": f"Bearer {valid_token}",
            "X-Idempotency-Key": "job-fail-502",
        },
    )
    assert response.status_code == 502
    assert "unreachable" in response.json()["detail"].lower()
