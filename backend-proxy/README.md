# WhatsApp Backend Proxy for Android Sales Caller

Lightweight, secure backend proxy service built with **Python 3**, **FastAPI**, and **Uvicorn**.

This service implements the proxy layer defined in `docs/backend-proxy.md`:
1. It maintains the master **WAHA API key** on the server, completely shielding credentials from the Android APK binary.
2. It validates short-lived, cryptographically signed device JWTs issued to authenticated handsets.
3. It requires an explicit `X-Idempotency-Key` header that exactly matches the request `jobId`; it never silently substitutes one.
4. It provides structured rate-limiting and payload validation.
5. It enforces a server-side live-send kill switch (`WAHA_ENABLE_LIVE`), explicit WhatsApp consent, and opt-out checks before any provider call.
6. It treats timeouts, 5xx, and unexpected post-dispatch errors as **unknown delivery**, not failure, and locks the idempotency key so an automatic retry cannot silently duplicate a message.

---

## Architecture Overview

```
[Physical Android Handset]
       │
       │ HTTP/HTTPS + Authorization: Bearer <device-jwt>
       │ Header: X-Idempotency-Key: <jobId>
       ▼
[Sales Caller Backend Proxy (FastAPI :8081)]
  ├── JWT Signature & Expiry Validator (HS256, 32+ byte secret)
  ├── Sliding-Window Rate Limiter
  ├── Idempotency Store (TTL-backed deduplication)
  └── WAHA Upstream Adapter (X-Api-Key injection)
       │
       │ Local HTTP POST /api/sendText
       │ Header: X-Api-Key: $WAHA_API_KEY
       │ Body: { session, chatId, text }
       ▼
[WAHA WhatsApp Gateway (Docker in WSL 2, 127.0.0.1:3000)]
```

WAHA is bound to WSL loopback only. The Android app sees only the FastAPI proxy.
The API key, session selection, and WhatsApp pairing stay server-side. See
[`waha/README.md`](waha/README.md) for the container, pairing, and HTTPS
deployment instructions.

---

## Directory Structure

```
mobile_app/backend-proxy/
├── .env.example            # Template for environment variables (no real secrets)
├── .gitignore              # Ignores .env, virtualenvs, bytecode, test cache
├── README.md               # Setup, startup, and testing guide (this file)
├── requirements.txt        # Pinned Python dependencies
├── config.py               # Settings loader with strict JWT secret strength enforcement
├── auth.py                 # JWT signing, validation, and claim verification
├── idempotency.py          # Thread-safe in-memory idempotency cache
├── limiter.py              # In-memory sliding window rate limiter
├── main.py                 # FastAPI application and route endpoints
├── generate_token.py       # Administrative CLI utility to generate device JWTs
├── upstream/
│   ├── base.py             # Abstract IWhatsAppProvider interface
│   ├── disabled.py         # Safe disabled provider (rejects mock dispatches)
│   ├── waakg.py            # Deprecated WA-AKG adapter (compatibility only)
│   └── waha.py             # Live WAHA adapter (sendText, JID mapping, preflight)
├── waha/                   # Local WAHA Docker setup and management scripts
└── tests/                  # Automated pytest suite
    ├── test_auth.py        # Token validation & secret strength tests
    ├── test_health.py      # Contract probe & provider health tests
    ├── test_message.py     # Message dispatch & error mapping tests
    ├── test_idempotency.py # Deduplication & rate-limiting tests
    ├── test_waha.py        # Mocked WAHA success/failure/health tests
    └── test_token_cli.py   # CLI token generator tests
```

---

## Step-by-Step Windows PowerShell Setup

### 1. Create and Activate Virtual Environment

From the project root:

```powershell
cd C:\Users\gurug\OneDrive\Desktop\gitsession\gitsession\development-projects\mobile_app\backend-proxy

# Create virtual environment
python -m venv .venv

# Activate in PowerShell
.\.venv\Scripts\Activate.ps1
```

*(If script execution is restricted in PowerShell, run `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` first).*

### 2. Install Dependencies

```powershell
.\.venv\Scripts\pip.exe install -r requirements.txt
```

### 3. Generate a Strong Secret & Configure `.env`

Create a `.env` file by copying `.env.example`:

```powershell
Copy-Item .env.example .env
```

Generate a cryptographically secure 64-character hex secret:

```powershell
.\.venv\Scripts\python.exe -c "import secrets; print(secrets.token_hex(32))"
```

Open `.env` in your editor and set:
- `JWT_SECRET=<the_generated_hex_string>`
- `PORT=8081`
- `HOST=0.0.0.0`
- `WHATSAPP_PROVIDER=disabled` for the safe default, or `waha` after the WAHA
  container and QR pairing are ready.
- `WAHA_ENABLE_LIVE=false` until you intentionally enable local live dispatch.
  Selecting `WHATSAPP_PROVIDER=waha` while this is `false` is supported: health
  probing and session status remain available, but every `POST /api/message`
  is rejected with `503` before any WAHA send call.
- `WAHA_BASE_URL=http://127.0.0.1:3000`
- `WAHA_API_KEY=<same key as backend-proxy/waha/.env>`
- `WAHA_SESSION=default`
- `WAHA_VERIFY_SESSION=true`

> **Security Rule:** Startup will immediately fail if `JWT_SECRET` is less than 32 characters or contains common placeholder words (`replace-with`, `secret`, `changeme`).

For the local WAHA container, pairing, and secret generation, follow
[`waha/README.md`](waha/README.md). Do not put the WAHA API key in the Android
app or commit either `.env` file.

> **Safety note:** `waha\setup-waha-env.ps1` writes `WAHA_ENABLE_LIVE=true` while
> generating the WAHA block. For device testing, change it back to `false`
> before starting the proxy. The root `README.md` shows the exact command.

### 4. Run Automated Tests

Verify code correctness and security validation:

```powershell
.\.venv\Scripts\pytest.exe -v
```

The test suite is designed to be incapable of a real WhatsApp send by default:

- `tests/conftest.py` forces `WHATSAPP_PROVIDER=disabled` and
  `WAHA_ENABLE_LIVE=false`.
- Tests that exercise dispatch replace the provider with a mock.
- `WahaProvider` refuses to construct without an `httpx.MockTransport` while
  `PYTEST_CURRENT_TEST` is set.
- The kill-switch regression test asserts that zero WAHA HTTP requests occur
  when live sending is disabled.

Current device-integration evidence (tests, APK hash, WAHA send count, and
device status) is recorded in
[`../docs/device-integration-readiness.md`](../docs/device-integration-readiness.md).

---

## Running the Backend

### Start Server Locally (recommended)

Use the launcher, which reads `PORT`/`HOST` from `.env`, checks for port conflicts, and prints the exact URLs to enter on the phone:

```powershell
.\run-proxy.ps1
```

Equivalent manual command:

```powershell
.\.venv\Scripts\python.exe -m uvicorn main:app --host 0.0.0.0 --port 8081 --reload
```

> **Port note:** this project uses **8081**. Port **8080** is commonly occupied by Apache/XAMPP on Windows, and the Android app no longer defaults to it. If `uvicorn` reports `WinError 10048` / "address already in use", another service owns the port — run `.\run-proxy.ps1` (it detects this) or choose another port.

---

## Issuing Device JWTs for Android

To generate a short-lived session token for an Android handset:

```powershell
.\.venv\Scripts\python.exe generate_token.py --device-id "pixel-agent-01" --expiry-minutes 60
```

This prints a signed JWT valid for 60 minutes.

---

## Testing Endpoints Locally

### 1. Test Health Probe (`GET /api/health`)

**Unauthenticated:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/health" -Method GET
```
*Expected response:*
```json
{
  "status": "UP",
  "proxy": "running",
  "version": "1.0.0",
  "provider": "waha",
  "live_send_enabled": false,
  "live_send_blocked_reason": "kill_switch_disabled",
  "auth": {
    "authenticated": false,
    "deviceId": null
  },
  "waha": {
    "configured": true,
    "provider": "waha",
    "status": "connected",
    "session_status": "WORKING",
    "session_live_enabled": true,
    "live_send_enabled": false,
    "live_enabled": false,
    "live_send_blocked_reason": "kill_switch_disabled"
  },
  "waakg": {
    "configured": true,
    "status": "connected",
    "live_enabled": false
  }
}
```

**Authenticated:**
```powershell
$token = "PASTE_TOKEN_HERE"
Invoke-RestMethod -Uri "http://localhost:8081/api/health" -Method GET -Headers @{ "Authorization" = "Bearer $token" }
```
*Expected response:*
```json
{
  "status": "UP",
  "proxy": "running",
  "version": "1.0.0",
  "provider": "waha",
  "live_send_enabled": false,
  "live_send_blocked_reason": "kill_switch_disabled",
  "auth": {
    "authenticated": true,
    "deviceId": "pixel-agent-01"
  },
  "waha": {
    "configured": true,
    "provider": "waha",
    "status": "connected",
    "session_status": "WORKING",
    "session_live_enabled": true,
    "live_send_enabled": false,
    "live_enabled": false,
    "live_send_blocked_reason": "kill_switch_disabled"
  }
}
```

### 2. Test Message Dispatch (`POST /api/message`)

```powershell
$body = @{
  sessionId = "default"
  jid = "919876543210@s.whatsapp.net"
  content = "Automated follow-up message for template: follow_up_intro"
  jobId = "test-job-001"
  templateName = "follow_up_intro"
  consent = $true
  optOut = $false
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/message" -Method POST -Headers @{
  "Authorization" = "Bearer $token"
  "Content-Type" = "application/json"
  "X-Idempotency-Key" = "test-job-001"
} -Body $body
```

The dispatch contract is deliberately strict:

- `X-Idempotency-Key` is required, must be 6–200 characters from
  `A-Z a-z 0-9 . _ : -`, and must exactly match `jobId`. A missing key returns
  `400`; the backend never substitutes `jobId`.
- `consent` defaults to `false`. Missing consent returns `403` and no provider
  call is made.
- `optOut=true` returns `403` even if consent is present.
- When `WAHA_ENABLE_LIVE=false`, the endpoint returns `503` before the consent
  check and before any provider call. The response includes
  `X-Delivery-Outcome: not_sent` and `X-Live-Send-Enabled: false`.
- A successful proxy acceptance returns `X-Delivery-Outcome: accepted`, which
  means accepted by the proxy/provider, **not** proof of WhatsApp delivery.
- A timeout, provider 5xx, or unexpected post-dispatch exception returns
  `X-Delivery-Outcome: unknown`, locks the idempotency key, and must not be
  retried automatically.

*When `WHATSAPP_PROVIDER=disabled`, or when WAHA is selected but the WhatsApp
session is not `WORKING`, the endpoint safely responds with HTTP 503 explaining
the gate, preventing fake delivery claims. A valid token plus an unpaired WAHA
session returns pairing guidance and does not call WAHA `sendText`.*

If WAHA times out or returns a 5xx after the request may have been accepted, the
proxy reports an **unknown delivery outcome** (`X-Delivery-Outcome: unknown`)
and caches that outcome under the idempotency key. Do not retry automatically.
Verify whether the message arrived first; a confirmed manual retry must use a
new `jobId` and matching `X-Idempotency-Key`.

---

## Connecting a Physical Android Phone on the Same Wi-Fi

1. **Find Host Development PC's Local IP:**
   ```powershell
   ipconfig
   ```
   Look for the `IPv4 Address` under your active Wi-Fi adapter (e.g. `192.168.1.50`).

2. **Allow Inbound Port 8081 on Windows Firewall:**
   In an elevated PowerShell prompt:
   ```powershell
   New-NetFirewallRule -DisplayName "Sales Caller Proxy Dev" -Direction Inbound -LocalPort 8081 -Protocol TCP -Action Allow
   ```

3. **USB fallback (no Wi-Fi / client isolation):**
   With USB debugging enabled:
   ```powershell
   adb reverse tcp:8081 tcp:8081
   ```
   Then use `http://127.0.0.1:8081` as the proxy URL in the app.

4. **Android Cleartext Traffic Requirement:**
   - Android 9+ (API 28+) blocks unencrypted HTTP (`http://...`) by default.
   - This project's debug manifest permits cleartext traffic for local development. For production, use HTTPS (Caddy / Nginx / mkcert).

5. **Configure in Sales Caller App:**
   - Open the **Sales Caller** app on the handset.
   - Tap **Settings**.
   - Under **Backend Proxy Base URL**, enter: `http://192.168.1.50:8081` (replacing with your actual PC Wi-Fi IP from `ipconfig`).
   - Under **JWT Session Token**, paste the token generated by `generate_token.py`.
   - Tap **Save Config**, then **Verify Contract**.
   - The status card shows **Proxy Contract Verified — Live dispatch ready** when the proxy is reachable, the WAHA session is `WORKING`, and live dispatch is enabled.
   - If it shows **Proxy Reachable — WhatsApp Dispatch Gated**, the network and JWT are fine, but the WAHA session still needs QR pairing, WAHA is unreachable, or `.env` has `WAHA_ENABLE_LIVE=false`. Message dispatch returns `503` until the gate is cleared. This is intentional: the proxy never fakes delivery.
   - In **Contacts**, enable **WA** consent for a test contact. Consent is off by default and a follow-up cannot be queued without it.
   - After a call, the agent must explicitly confirm the outcome and opt in to a follow-up. A completed call never queues or sends a message by itself.
   - In **WhatsApp Queue**, review the job and tap **Send Approved**. Jobs move through `PENDING → SENDING → ACCEPTED` (or `FAILED`). An `UNKNOWN` outcome requires manual verification in WhatsApp before the app will create a new key for a retry.
   - If the app shows a pairing message, open `http://localhost:3000/dashboard` on the server, or generate a fresh QR with `waha\manage-waha.ps1 -Action qr`, and scan it with the dedicated test phone. The QR expires quickly.

---

## Troubleshooting: "Gateway Offline"

The app reports **Gateway Offline** when the health request is refused, times out, or returns an unexpected status. If the URL reaches a different server (for example Apache on port 8080), the app reports **Configuration Incomplete — HTTP 404** instead, because the network path is fine but the URL is wrong.

| Phone/browser test | Meaning | Fix |
|---|---|---|
| `http://<PC-IP>:8081/api/health` returns JSON | Network is fine | Re-enter the exact URL + JWT in Settings and tap **Verify Contract** |
| Browser cannot load the URL | Phone cannot reach the PC | Put both on the same Wi-Fi, allow port 8081, or use `adb reverse` |
| JSON returns **404** | Reached a different server (often Apache on 8080) | Use port **8081**, not 8080 |
| `401` from the app | JWT expired/invalid | Regenerate with `generate_token.py` and paste the new token |
| Proxy is not running | Nothing listening | Run `.\run-proxy.ps1` and leave the window open |
| App says pairing required | WAHA session is `SCAN_QR_CODE` or `FAILED` | Start/restart the session and scan a fresh QR; see `waha/README.md` |
| App says WAHA rejected the API key | Backend and WAHA keys differ, or `.env` has CRLF | Align both `.env` files; regenerate with `waha\setup-waha-env.ps1 -Force` if needed |
| App says WAHA unreachable | WSL/Docker/container stopped | Run `waha\keep-wsl-alive.ps1`, then `waha\manage-waha.ps1 -Action up` |
