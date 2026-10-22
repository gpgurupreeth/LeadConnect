"""
Rate limiting module for backend proxy endpoints.
Implements an in-memory sliding-window counter per client identifier.
"""

import asyncio
import time
from typing import Dict, List


class SlidingWindowRateLimiter:
    """In-memory rate limiter using sliding timestamp window."""

    def __init__(self, max_requests: int = 60, window_seconds: int = 60):
        self.max_requests = max_requests
        self.window_seconds = window_seconds
        self._history: Dict[str, List[float]] = {}
        self._lock = asyncio.Lock()

    async def is_allowed(self, identifier: str) -> bool:
        """Check if request from identifier is within rate limits."""
        async with self._lock:
            now = time.time()
            cutoff = now - self.window_seconds

            timestamps = self._history.get(identifier, [])
            valid_timestamps = [ts for ts in timestamps if ts > cutoff]

            if len(valid_timestamps) >= self.max_requests:
                self._history[identifier] = valid_timestamps
                return False

            valid_timestamps.append(now)
            self._history[identifier] = valid_timestamps
            return True

    async def reset(self):
        """Clear rate limiter state."""
        async with self._lock:
            self._history.clear()
