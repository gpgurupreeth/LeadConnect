"""
Thread-safe in-memory idempotency store with TTL expiration.
Prevents duplicate message dispatch for identical job IDs.
"""

import asyncio
import time
from typing import Optional, Dict, Any, Tuple


class IdempotencyRecord:
    def __init__(self, status_code: int, response_body: Dict[str, Any], ttl_seconds: int):
        self.status_code = status_code
        self.response_body = response_body
        self.expires_at = time.time() + ttl_seconds
        self.in_flight = False

    def is_expired(self) -> bool:
        return time.time() > self.expires_at


class IdempotencyStore:
    """Thread-safe store for dispatched message idempotency keys."""

    def __init__(self, default_ttl_seconds: int = 86400):
        self._store: Dict[str, IdempotencyRecord] = {}
        self._lock = asyncio.Lock()
        self._default_ttl = default_ttl_seconds

    async def get(self, key: str) -> Optional[Tuple[int, Dict[str, Any]]]:
        """Retrieve stored response for an idempotency key if not expired."""
        async with self._lock:
            record = self._store.get(key)
            if not record:
                return None
            if record.is_expired():
                del self._store[key]
                return None
            if record.in_flight:
                return None
            return record.status_code, record.response_body

    async def start_inflight(self, key: str) -> bool:
        """
        Mark a key as currently in-flight.
        Returns False if key is already being processed or already completed.
        """
        async with self._lock:
            record = self._store.get(key)
            if record:
                if not record.is_expired():
                    return False  # Already present or in flight
                del self._store[key]

            new_record = IdempotencyRecord(
                status_code=0,
                response_body={},
                ttl_seconds=self._default_ttl,
            )
            new_record.in_flight = True
            self._store[key] = new_record
            return True

    async def complete(self, key: str, status_code: int, response_body: Dict[str, Any]):
        """Complete an in-flight record with the actual response."""
        async with self._lock:
            record = self._store.get(key)
            if record:
                record.status_code = status_code
                record.response_body = response_body
                record.expires_at = time.time() + self._default_ttl
                record.in_flight = False
            else:
                new_record = IdempotencyRecord(status_code, response_body, self._default_ttl)
                self._store[key] = new_record

    async def abort(self, key: str):
        """Abort an in-flight key so a retry can proceed if needed."""
        async with self._lock:
            if key in self._store and self._store[key].in_flight:
                del self._store[key]

    async def cleanup_expired(self):
        """Remove all expired records."""
        async with self._lock:
            now = time.time()
            expired = [k for k, v in self._store.items() if v.expires_at < now]
            for k in expired:
                del self._store[k]
