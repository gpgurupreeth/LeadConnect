# Backend Proxy Design

## Purpose
Provide a thin, secure service that stores the WAHA API key and forwards message‑submission requests from the Android app. The Android client never sees the API key or the WAHA session identifier.

## Architecture
```
Android App  <--HTTP(S) with JWT-->  Backend Proxy  <--local HTTP + X-Api-Key-->  WAHA (Docker in WSL 2)
                                                                                       |
                                                                                       v
                                                                              WhatsApp linked device
```
* **Authentication** – The app presents a short‑lived JWT issued by the backend. The proxy validates the JWT and injects the server-side `X-Api-Key` header for WAHA. The Android app never receives that key.
* **Session ownership** – The WAHA session is configured server-side with `WAHA_SESSION`. The Android `sessionId` field is kept for contract compatibility but does not override the server selection.
* **Idempotency** – The client must send an explicit `X-Idempotency-Key` that exactly matches `jobId`; the proxy never substitutes `jobId` automatically. A successful dispatch is replayed from the cache. A timeout, provider 5xx, or unexpected post-dispatch error is cached as an unknown-delivery outcome and the key is locked so automatic retries cannot silently send duplicates.
* **Live-send kill switch** – `WAHA_ENABLE_LIVE` defaults to `false`. When it is false, `POST /api/message` returns `503` with `X-Delivery-Outcome: not_sent` before any provider method or WAHA request. Health/session probing remains available.
* **Consent and opt-out** – The request carries explicit `consent` and `optOut` booleans. Missing consent or an opted-out recipient yields `403` before dispatch. Consent defaults to false.
* **Rate Limiting** – The FastAPI proxy enforces a per‑device sliding-window limit before dispatch.
* **Deployment** – Local Python (FastAPI) process plus WAHA in Docker. On Windows, run `backend-proxy\run-proxy.ps1` (default port `8081`). For remote use, put the proxy behind an HTTPS reverse proxy; keep WAHA on loopback/private networking.

## API Endpoints (example)
| Method | Path | Body | Description |
|--------|------|------|-------------|
| `POST` | `/api/message` | `{ "sessionId": "string", "jid": "string", "content": "string", "jobId": "string", "templateName": "string", "consent": true, "optOut": false }` + `X-Idempotency-Key: <jobId>` | Verify JWT, key/jobId match, rate limit, kill switch, and consent/opt-out, then normalize the JID and call WAHA `POST /api/sendText`. |
| `GET`  | `/api/health` | – | Liveness and provider status. Returns top-level `live_send_enabled`/`live_send_blocked_reason`, per-provider `session_live_enabled`, and a deprecated `waakg` compatibility alias. |

## Security Considerations
* **API Key** – Stored as `WAHA_API_KEY` in server-side environment configuration; never written to source code, logs, or the APK.
* **WAHA exposure** – The container publishes `127.0.0.1:3000:3000` inside WSL. It is not exposed on the LAN or internet.
* **TLS** – Local development may use HTTP on trusted localhost/LAN. Remote deployments must terminate TLS at a reverse proxy (Caddy/Nginx) with a valid certificate; release Android builds should require HTTPS.
* **JWT Secret** – Configured via env `JWT_SECRET`. Tokens contain `sub: deviceId` and `exp` claim.
* **No Shared Secret in APK** – The Android app only knows the proxy base URL; the WAHA key and JWT signing secret are server-side.
* **Pairing** – Pair only through WAHA's QR/pairing flow on a dedicated test phone. Never capture passwords or OTPs in the app.

## Failure Scenarios & Handling
1. **Proxy unavailable** – Android shows an offline/gateway message; queued jobs remain pending.
2. **Invalid/expired JWT** – Return `401`; the app prompts for a new token.
3. **Live-send kill switch disabled** – Return `503`, `X-Delivery-Outcome: not_sent`; no provider or WAHA send call. Health still reports session status.
4. **Missing consent or opted-out recipient** – Return `403`, `X-Delivery-Outcome: not_sent`; no provider call.
5. **WAHA session not `WORKING`** – Return `503` with pairing/connection guidance; do not call `sendText`.
6. **WAHA rejects a send** – Return a definitive error (`X-Delivery-Outcome: not_sent`) and release the idempotency key; never report fake success.
7. **WAHA timeout, 5xx, or unexpected post-dispatch error** – Return `X-Delivery-Outcome: unknown`, cache/lock the idempotency key, and do not automatically retry.
8. **Deterministic validation failure** – Return `4xx` and release the idempotency key so the operator can retry after fixing the request.

## Acceptance Criteria
* The WAHA API key never appears in the Android binary (verified for the current debug APK; see `docs/device-integration-readiness.md`).
* Local automated tests use mocked transports and a disabled provider; they cannot send a real WhatsApp message.
* JWT validation works; expired token yields `401`.
* A disabled kill switch or unpaired session yields `503` and does **not** call WAHA `sendText`.
* No authorized real-delivery test was performed during the readiness pass. One earlier unintended WAHA `sendText` request exists; its delivery is not confirmed and is recorded in `docs/device-integration-readiness.md`.
* Documentation includes local launch instructions (`run-proxy.ps1`, port `8081`), WAHA setup in `backend-proxy/waha/README.md`, and HTTPS deployment notes.
