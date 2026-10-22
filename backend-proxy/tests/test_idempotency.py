"""
Tests for message dispatch idempotency and rate limiting.
"""

import pytest
from limiter import SlidingWindowRateLimiter
from idempotency import IdempotencyStore


@pytest.mark.asyncio
async def test_idempotency_prevents_duplicate_sends(client, valid_token, mock_live_provider):
    payload = {
        "sessionId": "default",
        "jid": "919876543210@s.whatsapp.net",
        "content": "First attempt",
        "jobId": "idempotent-job-999",
        "templateName": "promo",
        "consent": True,
        "optOut": False,
    }
    headers = {
        "Authorization": f"Bearer {valid_token}",
        "X-Idempotency-Key": "idempotent-job-999",
    }

    # First dispatch
    res1 = await client.post("/api/message", json=payload, headers=headers)
    assert res1.status_code == 200
    assert len(mock_live_provider.calls) == 1
    msg_id_1 = res1.json()["messageId"]

    # Second dispatch with identical jobId
    res2 = await client.post("/api/message", json=payload, headers=headers)
    assert res2.status_code == 200
    msg_id_2 = res2.json()["messageId"]

    # Upstream MUST NOT have been called a second time
    assert len(mock_live_provider.calls) == 1
    assert msg_id_1 == msg_id_2


@pytest.mark.asyncio
async def test_sliding_window_rate_limiter():
    limiter = SlidingWindowRateLimiter(max_requests=3, window_seconds=60)
    assert await limiter.is_allowed("device-a") is True
    assert await limiter.is_allowed("device-a") is True
    assert await limiter.is_allowed("device-a") is True
    # 4th request in same window should be rejected
    assert await limiter.is_allowed("device-a") is False
    # Different device should still be allowed
    assert await limiter.is_allowed("device-b") is True


@pytest.mark.asyncio
async def test_idempotency_store_lifecycle():
    store = IdempotencyStore(default_ttl_seconds=10)
    key = "job-unit-test"

    assert await store.get(key) is None
    assert await store.start_inflight(key) is True
    # In-flight returns None for get
    assert await store.get(key) is None
    # Cannot start again while in-flight
    assert await store.start_inflight(key) is False

    await store.complete(key, 200, {"success": True, "messageId": "msg-123"})
    cached = await store.get(key)
    assert cached is not None
    assert cached[0] == 200
    assert cached[1]["messageId"] == "msg-123"
