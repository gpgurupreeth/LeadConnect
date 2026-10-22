"""
WAHA provider and proxy integration tests.

All HTTP traffic is mocked with httpx.MockTransport. These tests never contact
a real WAHA instance and never send a WhatsApp message.
"""

import json
import logging

import httpx
import pytest

from config import Settings
from upstream.waha import WahaProvider, extract_message_id


TEST_API_KEY = "waha-test-api-key-0123456789abcdef"
TEST_SESSION = "sales"
WAHA_CHAT_ID = "919876543210@c.us"
WAHA_MESSAGE_ID = "false_919876543210@c.us_AAAAAAAAAAAAAAAAAAAA"


def make_provider(handler, **kwargs):
    return WahaProvider(
        base_url="http://waha.test",
        api_key=TEST_API_KEY,
        session=TEST_SESSION,
        timeout_seconds=1.0,
        transport=httpx.MockTransport(handler),
        **kwargs,
    )


def sent_message_payload(content: str = "Follow up", job_id: str = "job-waha-001"):
    return {
        "sessionId": "default",
        "jid": "919876543210@s.whatsapp.net",
        "content": content,
        "jobId": job_id,
        "templateName": "follow_up",
        "consent": True,
        "optOut": False,
    }


class Recorder:
    def __init__(self, post_response=None, post_exception=None, session_status="WORKING"):
        self.requests = []
        self.post_response = post_response
        self.post_exception = post_exception
        self.session_status = session_status
        self.post_count = 0

    def __call__(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        if request.method == "GET" and request.url.path == "/health":
            return httpx.Response(200, json={"status": "ok"})
        if request.method == "GET" and request.url.path == f"/api/sessions/{TEST_SESSION}":
            return httpx.Response(
                200,
                json={"name": TEST_SESSION, "status": self.session_status},
            )
        if request.method == "POST" and request.url.path == "/api/sendText":
            self.post_count += 1
            if self.post_exception is not None:
                raise self.post_exception
            return self.post_response or httpx.Response(
                201,
                json={"id": WAHA_MESSAGE_ID, "ack": 1, "body": "not logged"},
            )
        return httpx.Response(404, json={"message": "unexpected path"})


# ---------------------------------------------------------------------------
# JID normalization
# ---------------------------------------------------------------------------

@pytest.mark.parametrize(
    "jid, expected",
    [
        ("919876543210@s.whatsapp.net", WAHA_CHAT_ID),
        ("+91 98765-43210@s.whatsapp.net", WAHA_CHAT_ID),
        ("919876543210@c.us", WAHA_CHAT_ID),
        ("+919876543210", WAHA_CHAT_ID),
        ("00919876543210", WAHA_CHAT_ID),
    ],
)
def test_normalize_chat_id_accepts_android_and_e164_formats(jid, expected):
    assert WahaProvider.normalize_chat_id(jid) == expected


@pytest.mark.parametrize(
    "jid",
    [
        "",
        "not-a-number",
        "12345",
        "09876543210",
        "1234567890@evil.example",
        "120363000000000000@g.us",
    ],
)
def test_normalize_chat_id_rejects_invalid_or_group_recipients(jid):
    with pytest.raises(ValueError):
        WahaProvider.normalize_chat_id(jid)


# ---------------------------------------------------------------------------
# Provider send behaviour
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_waha_send_success_maps_android_jid_and_server_session():
    recorder = Recorder()
    provider = make_provider(recorder)

    result = await provider.send_message(
        session_id="default",
        jid="919876543210@s.whatsapp.net",
        content="Hello from the proxy",
        job_id="job-waha-001",
        template_name="follow_up",
    )

    assert result.success is True
    assert result.message_id == WAHA_MESSAGE_ID
    assert result.status == "sent"
    assert result.duplicate_risk is False
    assert recorder.post_count == 1

    post = next(r for r in recorder.requests if r.method == "POST")
    assert post.url.path == "/api/sendText"
    assert post.headers["X-Api-Key"] == TEST_API_KEY
    body = json.loads(post.content.decode("utf-8"))
    assert body == {
        "session": TEST_SESSION,  # server configuration wins over Android's sessionId
        "chatId": WAHA_CHAT_ID,
        "text": "Hello from the proxy",
    }


@pytest.mark.parametrize(
    "data, expected",
    [
        ({"id": WAHA_MESSAGE_ID}, WAHA_MESSAGE_ID),
        ({"_data": {"id": {"_serialized": WAHA_MESSAGE_ID}}}, WAHA_MESSAGE_ID),
        ({"_data": {"key": {"id": WAHA_MESSAGE_ID}}}, WAHA_MESSAGE_ID),
        ({"id": {"id": WAHA_MESSAGE_ID}}, WAHA_MESSAGE_ID),
        ({"status": "accepted"}, None),
        ({"id": ""}, None),
        (None, None),
    ],
)
def test_extract_message_id_never_invents_an_acknowledgement(data, expected):
    assert extract_message_id(data) == expected


@pytest.mark.asyncio
async def test_waha_success_without_provider_id_returns_none():
    recorder = Recorder(post_response=httpx.Response(201, json={"status": "accepted", "ack": 0}))
    provider = make_provider(recorder)

    result = await provider.send_message(
        session_id="default",
        jid="919876543210@s.whatsapp.net",
        content="Accepted without an ID",
        job_id="job-no-id",
        template_name="follow_up",
    )

    assert result.success is True
    assert result.message_id is None
    assert result.status == "sent"
    assert recorder.post_count == 1


@pytest.mark.asyncio
async def test_waha_disconnected_session_is_detected_before_send():
    recorder = Recorder(session_status="SCAN_QR_CODE")
    provider = make_provider(recorder)

    result = await provider.send_message(
        session_id="default",
        jid="919876543210@s.whatsapp.net",
        content="Should not be sent",
        job_id="job-disconnected",
        template_name="follow_up",
    )

    assert result.success is False
    assert result.status_code == 503
    assert result.status == "session_not_connected"
    assert "pair" in result.error.lower() or "qr" in result.error.lower()
    assert recorder.post_count == 0


@pytest.mark.asyncio
async def test_waha_api_key_rejection_is_mapped_without_leaking_secret():
    recorder = Recorder()

    def handler(request: httpx.Request) -> httpx.Response:
        recorder.requests.append(request)
        return httpx.Response(401, json={"message": "Unauthorized"})

    provider = make_provider(handler)
    result = await provider.send_message(
        session_id="default",
        jid="919876543210@s.whatsapp.net",
        content="Should not be sent",
        job_id="job-auth-failure",
        template_name="follow_up",
    )

    assert result.success is False
    assert result.status_code == 502
    assert result.status == "unauthorized"
    assert TEST_API_KEY not in (result.error or "")
    assert recorder.post_count == 0


@pytest.mark.asyncio
async def test_waha_send_timeout_is_marked_duplicate_risk():
    recorder = Recorder(post_exception=httpx.ReadTimeout("read timed out"))
    provider = make_provider(recorder)

    result = await provider.send_message(
        session_id="default",
        jid="919876543210@s.whatsapp.net",
        content="Unknown outcome",
        job_id="job-timeout",
        template_name="follow_up",
    )

    assert result.success is False
    assert result.status_code == 504
    assert result.status == "timeout"
    assert result.duplicate_risk is True
    assert "do not retry" in result.error.lower()


@pytest.mark.asyncio
async def test_waha_connect_error_is_not_marked_duplicate_risk():
    recorder = Recorder(post_exception=httpx.ConnectError("connection refused"))
    provider = make_provider(recorder)

    result = await provider.send_message(
        session_id="default",
        jid="919876543210@s.whatsapp.net",
        content="Not sent",
        job_id="job-connect-error",
        template_name="follow_up",
    )

    assert result.success is False
    assert result.status_code == 502
    assert result.status == "unreachable"
    assert result.duplicate_risk is False


@pytest.mark.asyncio
async def test_waha_server_error_is_marked_duplicate_risk():
    recorder = Recorder(post_response=httpx.Response(500, json={"message": "boom"}))
    provider = make_provider(recorder)

    result = await provider.send_message(
        session_id="default",
        jid="919876543210@s.whatsapp.net",
        content="Unknown outcome",
        job_id="job-5xx",
        template_name="follow_up",
    )

    assert result.success is False
    assert result.status_code == 502
    assert result.status == "unknown"
    assert result.duplicate_risk is True


@pytest.mark.asyncio
async def test_waha_rate_limit_is_mapped_and_not_marked_duplicate_risk():
    recorder = Recorder(post_response=httpx.Response(429, json={"message": "slow down"}))
    provider = make_provider(recorder)

    result = await provider.send_message(
        session_id="default",
        jid="919876543210@s.whatsapp.net",
        content="Not sent",
        job_id="job-429",
        template_name="follow_up",
    )

    assert result.success is False
    assert result.status_code == 429
    assert result.status == "rate_limited"
    assert result.duplicate_risk is False


@pytest.mark.asyncio
async def test_waha_rejected_recipient_is_mapped_to_400():
    recorder = Recorder(post_response=httpx.Response(400, json={"message": "invalid chatId"}))
    provider = make_provider(recorder)

    result = await provider.send_message(
        session_id="default",
        jid="919876543210@s.whatsapp.net",
        content="Rejected",
        job_id="job-invalid",
        template_name="follow_up",
    )

    assert result.success is False
    assert result.status_code == 400
    assert result.status == "invalid_recipient"
    assert result.duplicate_risk is False


@pytest.mark.asyncio
async def test_waha_success_without_message_id_still_reports_success_from_2xx():
    recorder = Recorder(post_response=httpx.Response(201, json={}))
    provider = make_provider(recorder)

    result = await provider.send_message(
        session_id="default",
        jid="919876543210@s.whatsapp.net",
        content="Accepted",
        job_id="job-no-id",
        template_name="follow_up",
    )

    assert result.success is True
    assert result.message_id is None
    assert result.status == "sent"


@pytest.mark.asyncio
async def test_invalid_recipient_is_rejected_without_any_network_call():
    requests = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(500)

    provider = make_provider(handler)
    result = await provider.send_message(
        session_id="default",
        jid="not-a-number",
        content="Rejected locally",
        job_id="job-bad-jid",
        template_name="follow_up",
    )

    assert result.success is False
    assert result.status_code == 400
    assert result.status == "invalid_recipient"
    assert requests == []


# ---------------------------------------------------------------------------
# Health probe
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_waha_health_reports_connected_session():
    recorder = Recorder(session_status="WORKING")
    provider = make_provider(recorder)

    health = await provider.check_health()

    assert health["configured"] is True
    assert health["provider"] == "waha"
    assert health["status"] == "connected"
    assert health["live_enabled"] is True
    assert health["session_status"] == "WORKING"


@pytest.mark.asyncio
async def test_waha_health_reports_qr_pairing_state():
    recorder = Recorder(session_status="SCAN_QR_CODE")
    provider = make_provider(recorder)

    health = await provider.check_health()

    assert health["status"] == "scan_qr"
    assert health["live_enabled"] is False
    assert "qr" in health["message"].lower()


@pytest.mark.asyncio
async def test_waha_health_reports_auth_failure():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(401, json={"message": "Unauthorized"})

    provider = make_provider(handler)
    health = await provider.check_health()

    assert health["status"] == "unauthorized"
    assert health["live_enabled"] is False


@pytest.mark.asyncio
async def test_waha_health_reports_missing_session():
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/health":
            return httpx.Response(200, json={"status": "ok"})
        return httpx.Response(404, json={"message": "Not found"})

    provider = make_provider(handler)
    health = await provider.check_health()

    assert health["status"] == "session_not_found"
    assert health["live_enabled"] is False


@pytest.mark.asyncio
async def test_waha_health_reports_unreachable():
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused")

    provider = make_provider(handler)
    health = await provider.check_health()

    assert health["status"] == "unreachable"
    assert health["live_enabled"] is False


@pytest.mark.asyncio
async def test_waha_health_never_contains_api_key():
    recorder = Recorder(session_status="WORKING")
    provider = make_provider(recorder)

    health = await provider.check_health()

    assert TEST_API_KEY not in json.dumps(health)


@pytest.mark.asyncio
async def test_waha_logs_do_not_contain_api_key_phone_or_message(caplog):
    caplog.set_level(logging.WARNING)
    recorder = Recorder(post_response=httpx.Response(500, json={"message": "boom"}))
    provider = make_provider(recorder)

    await provider.send_message(
        session_id="default",
        jid="919876543210@s.whatsapp.net",
        content="Sensitive customer message body",
        job_id="job-log-check",
        template_name="follow_up",
    )

    log_text = caplog.text
    assert TEST_API_KEY not in log_text
    assert "919876543210" not in log_text
    assert "Sensitive customer message body" not in log_text


# ---------------------------------------------------------------------------
# Proxy integration with a mocked WAHA transport
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_proxy_waha_success_returns_android_compatible_response(
    client, valid_token, provider_swap
):
    recorder = Recorder()
    provider_swap(make_provider(recorder))

    response = await client.post(
        "/api/message",
        json=sent_message_payload(),
        headers={
            "Authorization": f"Bearer {valid_token}",
            "X-Idempotency-Key": "job-waha-001",
        },
    )

    assert response.status_code == 200
    data = response.json()
    assert data == {
        "success": True,
        "messageId": WAHA_MESSAGE_ID,
        "status": "sent",
    }
    assert recorder.post_count == 1


@pytest.mark.asyncio
async def test_proxy_waha_success_without_provider_id_returns_null_message_id(
    client, valid_token, provider_swap
):
    recorder = Recorder(post_response=httpx.Response(201, json={"status": "accepted", "ack": 0}))
    provider_swap(make_provider(recorder))

    response = await client.post(
        "/api/message",
        json=sent_message_payload(job_id="job-no-id"),
        headers={
            "Authorization": f"Bearer {valid_token}",
            "X-Idempotency-Key": "job-no-id",
        },
    )

    assert response.status_code == 200
    data = response.json()
    assert data["success"] is True
    assert data["messageId"] is None
    assert data["status"] == "sent"
    assert recorder.post_count == 1


@pytest.mark.asyncio
async def test_proxy_waha_success_replay_does_not_resend(client, valid_token, provider_swap):
    recorder = Recorder()
    provider_swap(make_provider(recorder))

    headers = {
        "Authorization": f"Bearer {valid_token}",
        "X-Idempotency-Key": "job-waha-001",
    }
    first = await client.post("/api/message", json=sent_message_payload(), headers=headers)
    second = await client.post("/api/message", json=sent_message_payload(), headers=headers)

    assert first.status_code == 200
    assert second.status_code == 200
    assert first.json()["messageId"] == second.json()["messageId"]
    assert recorder.post_count == 1


@pytest.mark.asyncio
async def test_proxy_waha_timeout_locks_idempotency_key(client, valid_token, provider_swap):
    recorder = Recorder(post_exception=httpx.ReadTimeout("read timed out"))
    provider_swap(make_provider(recorder))

    headers = {
        "Authorization": f"Bearer {valid_token}",
        "X-Idempotency-Key": "job-timeout",
    }
    payload = {**sent_message_payload(), "jobId": "job-timeout"}

    first = await client.post("/api/message", json=payload, headers=headers)
    second = await client.post("/api/message", json=payload, headers=headers)

    assert first.status_code == 504
    assert second.status_code == 504
    assert "do not retry" in first.json()["detail"].lower()
    assert second.headers["x-delivery-outcome"] == "unknown"
    assert recorder.post_count == 1  # the retry must not reach WAHA again


@pytest.mark.asyncio
async def test_proxy_waha_5xx_locks_idempotency_key(client, valid_token, provider_swap):
    recorder = Recorder(post_response=httpx.Response(500, json={"message": "boom"}))
    provider_swap(make_provider(recorder))

    headers = {
        "Authorization": f"Bearer {valid_token}",
        "X-Idempotency-Key": "job-5xx",
    }
    payload = {**sent_message_payload(), "jobId": "job-5xx"}

    first = await client.post("/api/message", json=payload, headers=headers)
    second = await client.post("/api/message", json=payload, headers=headers)

    assert first.status_code == 502
    assert second.status_code == 502
    assert second.headers["x-delivery-outcome"] == "unknown"
    assert recorder.post_count == 1


@pytest.mark.asyncio
async def test_proxy_waha_disconnected_session_returns_503_without_send(
    client, valid_token, provider_swap
):
    recorder = Recorder(session_status="SCAN_QR_CODE")
    provider_swap(make_provider(recorder))

    response = await client.post(
        "/api/message",
        json=sent_message_payload(job_id="job-disconnected"),
        headers={
            "Authorization": f"Bearer {valid_token}",
            "X-Idempotency-Key": "job-disconnected",
        },
    )

    assert response.status_code == 503
    assert "pair" in response.json()["detail"].lower() or "qr" in response.json()["detail"].lower()
    assert recorder.post_count == 0


@pytest.mark.asyncio
async def test_proxy_waha_auth_failure_returns_502_without_leaking_secret(
    client, valid_token, provider_swap
):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(401, json={"message": "Unauthorized"})

    provider_swap(make_provider(handler))

    response = await client.post(
        "/api/message",
        json=sent_message_payload(job_id="job-auth"),
        headers={
            "Authorization": f"Bearer {valid_token}",
            "X-Idempotency-Key": "job-auth",
        },
    )

    assert response.status_code == 502
    assert TEST_API_KEY not in response.text


@pytest.mark.asyncio
async def test_proxy_waha_invalid_recipient_returns_400(client, valid_token, provider_swap):
    recorder = Recorder(post_response=httpx.Response(400, json={"message": "invalid chatId"}))
    provider_swap(make_provider(recorder))

    response = await client.post(
        "/api/message",
        json=sent_message_payload(job_id="job-400"),
        headers={
            "Authorization": f"Bearer {valid_token}",
            "X-Idempotency-Key": "job-400",
        },
    )

    assert response.status_code == 400
    assert "recipient" in response.json()["detail"].lower()


@pytest.mark.asyncio
async def test_proxy_kill_switch_blocks_dispatch_without_any_waha_request(
    client, valid_token, provider_swap
):
    import main as app_module

    recorder = Recorder()
    provider_swap(make_provider(recorder))
    app_module.settings.WAHA_ENABLE_LIVE = False

    response = await client.post(
        "/api/message",
        json=sent_message_payload(job_id="job-kill-switch"),
        headers={
            "Authorization": f"Bearer {valid_token}",
            "X-Idempotency-Key": "job-kill-switch",
        },
    )

    assert response.status_code == 503
    assert response.headers["x-delivery-outcome"] == "not_sent"
    assert response.headers["x-live-send-enabled"] == "false"
    assert "kill switch" in response.json()["detail"].lower()
    assert recorder.post_count == 0
    assert recorder.requests == []


@pytest.mark.asyncio
async def test_proxy_requires_matching_idempotency_key_before_dispatch(
    client, valid_token, provider_swap
):
    recorder = Recorder()
    provider_swap(make_provider(recorder))

    missing = await client.post(
        "/api/message",
        json=sent_message_payload(job_id="job-missing-key"),
        headers={"Authorization": f"Bearer {valid_token}"},
    )
    assert missing.status_code == 400
    assert missing.headers["x-delivery-outcome"] == "not_sent"
    assert "x-idempotency-key" in missing.json()["detail"].lower()

    mismatch = await client.post(
        "/api/message",
        json=sent_message_payload(job_id="job-mismatch"),
        headers={
            "Authorization": f"Bearer {valid_token}",
            "X-Idempotency-Key": "job-different-key",
        },
    )
    assert mismatch.status_code == 400
    assert "must match" in mismatch.json()["detail"].lower()

    invalid = await client.post(
        "/api/message",
        json=sent_message_payload(job_id="job-invalid-key"),
        headers={
            "Authorization": f"Bearer {valid_token}",
            "X-Idempotency-Key": "bad key",
        },
    )
    assert invalid.status_code == 400
    assert recorder.post_count == 0
    assert recorder.requests == []


@pytest.mark.asyncio
async def test_proxy_requires_consent_and_rejects_opt_out(client, valid_token, provider_swap):
    recorder = Recorder()
    provider_swap(make_provider(recorder))

    no_consent = sent_message_payload(job_id="job-no-consent")
    no_consent["consent"] = False
    response = await client.post(
        "/api/message",
        json=no_consent,
        headers={
            "Authorization": f"Bearer {valid_token}",
            "X-Idempotency-Key": "job-no-consent",
        },
    )
    assert response.status_code == 403
    assert "consent" in response.json()["detail"].lower()
    assert response.headers["x-delivery-outcome"] == "not_sent"

    opted_out = sent_message_payload(job_id="job-opt-out")
    opted_out["optOut"] = True
    response = await client.post(
        "/api/message",
        json=opted_out,
        headers={
            "Authorization": f"Bearer {valid_token}",
            "X-Idempotency-Key": "job-opt-out",
        },
    )
    assert response.status_code == 403
    assert "opted out" in response.json()["detail"].lower()
    assert recorder.post_count == 0
    assert recorder.requests == []


class ExplodingProvider:
    """Simulates a provider that raises after it may have reached WAHA."""

    def __init__(self):
        self.calls = 0

    async def send_message(self, **kwargs):
        self.calls += 1
        raise RuntimeError("simulated failure after dispatch attempt")

    async def check_health(self):
        return {"configured": True, "status": "connected", "live_enabled": True}


@pytest.mark.asyncio
async def test_proxy_unexpected_exception_locks_key_as_unknown(client, valid_token, provider_swap):
    provider = ExplodingProvider()
    provider_swap(provider)

    headers = {
        "Authorization": f"Bearer {valid_token}",
        "X-Idempotency-Key": "job-exploding",
    }
    payload = sent_message_payload(job_id="job-exploding")

    first = await client.post("/api/message", json=payload, headers=headers)
    second = await client.post("/api/message", json=payload, headers=headers)

    assert first.status_code == 500
    assert first.headers["x-delivery-outcome"] == "unknown"
    assert second.status_code == 500
    assert second.headers["x-delivery-outcome"] == "unknown"
    assert "verify in whatsapp" in second.json()["detail"].lower()
    assert provider.calls == 1


@pytest.mark.asyncio
async def test_proxy_health_exposes_waha_and_legacy_alias(client, valid_token, provider_swap):
    recorder = Recorder(session_status="WORKING")
    provider_swap(make_provider(recorder))

    response = await client.get(
        "/api/health",
        headers={"Authorization": f"Bearer {valid_token}"},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["waha"]["status"] == "connected"
    assert data["waha"]["live_enabled"] is True
    assert data["waakg"] == data["waha"]  # deprecated compatibility alias
    assert TEST_API_KEY not in response.text


# ---------------------------------------------------------------------------
# Configuration safety
# ---------------------------------------------------------------------------

def test_provider_defaults_to_disabled():
    settings = Settings(
        JWT_SECRET="a9f8b7c6d5e4a3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8"
    )
    assert settings.WHATSAPP_PROVIDER == "disabled"
    assert settings.WAHA_ENABLE_LIVE is False


def test_provider_rejects_unknown_value():
    with pytest.raises(ValueError):
        Settings(
            JWT_SECRET="a9f8b7c6d5e4a3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8",
            WHATSAPP_PROVIDER="waakg",
        )


def test_provider_rejects_invalid_session_name():
    with pytest.raises(ValueError):
        Settings(
            JWT_SECRET="a9f8b7c6d5e4a3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8",
            WHATSAPP_PROVIDER="waha",
            WAHA_SESSION="../escape",
        )


@pytest.mark.asyncio
async def test_provider_selection_disables_waha_without_credentials(monkeypatch):
    import main as app_module
    from upstream.disabled import DisabledProvider

    original_get_settings = app_module.get_settings
    test_settings = Settings(
        JWT_SECRET="a9f8b7c6d5e4a3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8",
        WHATSAPP_PROVIDER="waha",
        WAHA_ENABLE_LIVE=True,
        WAHA_BASE_URL="http://127.0.0.1:3000",
        WAHA_API_KEY=None,
        WAHA_SESSION="default",
    )
    monkeypatch.setattr(app_module, "get_settings", lambda: test_settings)
    try:
        app_module.init_app_state()
        assert isinstance(app_module.upstream_provider, DisabledProvider)
    finally:
        app_module.get_settings = original_get_settings
        app_module.init_app_state()
