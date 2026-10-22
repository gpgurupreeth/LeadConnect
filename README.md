# Sales Calling & WhatsApp Automation

Local-first Android app for sequential SIM-based sales calls and **explicitly approved** WhatsApp follow-ups. WhatsApp dispatch goes through a FastAPI proxy, which keeps the WAHA API key off the phone and enforces the live-send gate.

## Current Readiness

| Check | Status |
|---|---|
| Backend tests | **75 passed** |
| Android unit tests | **79 tests, 0 failures** |
| Debug APK | **Built** — `app/build/outputs/apk/debug/app-debug.apk` |
| APK SHA-256 | `C45F53AF7172AD161B865DFA2EB3FD6EB73C4D59CB82E11B6BE78E01E0FDBA1A` |
| Live WhatsApp sending | **Disabled** (`WAHA_ENABLE_LIVE=false`) |
| Device installation / UI / SIM-call testing | **BLOCKED** — no USB device attached |
| Real WhatsApp delivery | **NOT TESTED** — not authorized |

Full commands, evidence, the historical WAHA send count, and the device checklist are in **[docs/device-integration-readiness.md](docs/device-integration-readiness.md)**.

## Safety Model

- `WAHA_ENABLE_LIVE` defaults to `false`; dispatch returns `503` without any WAHA send request.
- The proxy requires `X-Idempotency-Key` and an exact match with `jobId`. It never substitutes `jobId` automatically.
- Consent is explicit and defaults to `false`; opted-out recipients are refused.
- Timeouts, provider 5xx, and unexpected post-dispatch errors become `UNKNOWN`, not failure. Automatic retries are blocked.
- A manual retry after `UNKNOWN` requires verification and a new idempotency key.
- A call ending never queues or sends a follow-up by itself. The agent must confirm the outcome and opt in.
- Backend tests use a disabled provider, mocked transports, and a test-time constructor guard, so the suite cannot send a real WhatsApp message.

## Project Layout

```text
mobile_app/
├── app/                  Android app (Kotlin, Jetpack Compose, Room)
├── backend-proxy/        FastAPI proxy, WAHA adapter, tests
├── docs/                 Architecture, proxy, idempotency, readiness evidence
├── gradlew.bat
└── README.md
```

## Before You Start

- Windows 10/11 with PowerShell.
- Python 3.10+ for the backend.
- JDK and Android SDK for the Gradle build.
- WSL 2 with Ubuntu for the local WAHA container.
- An Android phone with USB debugging enabled (for device testing).
- The dedicated test phone must be linked to WAHA through WhatsApp **Linked devices**.

## Configuration Files

| File | Purpose | How it is created |
|---|---|---|
| `backend-proxy/.env` | Proxy port, JWT secret, WAHA URL/key, safety gates | Copy `.env.example`, then edit |
| `backend-proxy/waha/.env` | WAHA API key and dashboard credentials | `setup-waha-env.ps1` |
| `app/local.properties` | Local Android SDK path | Android Studio / Gradle |
| APK | Debug test build | `.\gradlew.bat assembleDebug` |

`backend-proxy/.env` and `backend-proxy/waha/.env` are **git-ignored**. Never commit them, paste their values into chat, or put the WAHA key in the Android app.

## Step-by-Step Setup

### Step 1 — Install backend dependencies

```powershell
cd C:\Users\gurug\OneDrive\Desktop\gitsession\gitsession\development-projects\mobile_app\backend-proxy

# First time only
python -m venv .venv
.\.venv\Scripts\pip.exe install -r requirements.txt
```

If PowerShell blocks the virtual-environment activation script, use the executables directly as shown above, or run:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

### Step 2 — Create `backend-proxy/.env` and set `JWT_SECRET`

Create the file from the template:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Generate a 64-character JWT secret and write it into `.env` **without printing the secret**:

```powershell
$secret = .\.venv\Scripts\python.exe -c "import secrets; print(secrets.token_hex(32))"
$envPath = (Resolve-Path .env).Path
$text = [System.IO.File]::ReadAllText($envPath)
$text = [regex]::Replace($text, '(?m)^JWT_SECRET=.*$', "JWT_SECRET=$secret")
[System.IO.File]::WriteAllText($envPath, $text, (New-Object System.Text.UTF8Encoding($false)))
```

The backend refuses to start if `JWT_SECRET` is shorter than 32 characters or contains placeholder words such as `replace-with`, `secret`, or `changeme`.

The other important values in `backend-proxy/.env` are:

| Key | Safe local value | Meaning |
|---|---|---|
| `PORT` | `8081` | Backend proxy port |
| `HOST` | `0.0.0.0` | Allows the phone on the same Wi-Fi to reach the proxy. Use `127.0.0.1` only with `adb reverse`. |
| `ENVIRONMENT` | `development` | Local development mode |
| `JWT_EXPIRATION_MINUTES` | `5` | Short-lived device-token lifetime |
| `WHATSAPP_PROVIDER` | `waha` after WAHA exists; `disabled` otherwise | Selects the server-side WAHA adapter |
| `WAHA_ENABLE_LIVE` | **`false`** | Master send switch. Keep it off for all setup and queue tests. |
| `WAHA_BASE_URL` | `http://127.0.0.1:3000` | Local WAHA endpoint |
| `WAHA_API_KEY` | Same value as `waha/.env` | Injected by the proxy; never used by Android |
| `WAHA_SESSION` | `default` | Server-side WAHA session name |
| `WAHA_VERIFY_SESSION` | `true` | Checks the session before dispatch |

### Step 3 — Create `backend-proxy/waha/.env`

Run the WAHA secret generator from its directory:

```powershell
cd waha
.\setup-waha-env.ps1
```

It generates `waha/.env` with:

- `WAHA_API_KEY`
- `WAHA_DASHBOARD_USERNAME`
- `WAHA_DASHBOARD_PASSWORD`

It also copies the generated API key into `backend-proxy/.env` and sets `WHATSAPP_PROVIDER=waha`.

> **Important safety step:** `setup-waha-env.ps1` writes `WAHA_ENABLE_LIVE=true`. Change it back to `false` immediately after setup, before starting the proxy or testing the phone:

```powershell
$envPath = (Resolve-Path ..\.env).Path
$text = [System.IO.File]::ReadAllText($envPath)
$text = [regex]::Replace($text, '(?m)^WAHA_ENABLE_LIVE=.*$', 'WAHA_ENABLE_LIVE=false')
[System.IO.File]::WriteAllText($envPath, $text, (New-Object System.Text.UTF8Encoding($false)))
```

Do not run `.\setup-waha-env.ps1 -Force` unless you intend to rotate the WAHA API key. After rotation, recreate the WAHA container and restart the proxy.

Verify that the WAHA `.env` uses LF line endings and does not contain CRLF:

```powershell
$raw = Get-Content -Raw .\.env
if ($raw.Contains("`r")) { "CRLF detected — rerun setup-waha-env.ps1" } else { "LF-only: OK" }
```

CRLF in `waha/.env` can produce a malformed `X-Api-Key` header in WSL.

### Step 4 — Run backend tests

```powershell
cd C:\Users\gurug\OneDrive\Desktop\gitsession\gitsession\development-projects\mobile_app\backend-proxy
.\.venv\Scripts\pytest.exe -q
```

Expected: `75 passed`. The test suite forces a disabled provider, uses mocked transports, and cannot send a real WhatsApp message.

### Step 5 — Start WAHA and pair the test phone

If Docker Engine is not already installed inside WSL, follow
[`backend-proxy/waha/README.md`](backend-proxy/waha/README.md) section 1 first.
Docker Desktop is not required.

Open a **separate PowerShell window** and keep WSL alive:

```powershell
cd C:\Users\gurug\OneDrive\Desktop\gitsession\gitsession\development-projects\mobile_app\backend-proxy\waha
.\keep-wsl-alive.ps1
```

Leave that window open. In another PowerShell window:

```powershell
cd C:\Users\gurug\OneDrive\Desktop\gitsession\gitsession\development-projects\mobile_app\backend-proxy\waha
.\manage-waha.ps1 -Action up
.\manage-waha.ps1 -Action wait-health
.\manage-waha.ps1 -Action start-session -Session default
.\manage-waha.ps1 -Action qr -Session default
```

Scan `backend-proxy/waha/qr-default.png` with the dedicated test phone:

**WhatsApp → Linked devices → Link a device**

Then confirm the session is working:

```powershell
.\manage-waha.ps1 -Action health -Session default
```

Wait for `WORKING`. If the QR expires, restart the session and generate a fresh QR immediately:

```powershell
.\manage-waha.ps1 -Action restart-session -Session default
.\manage-waha.ps1 -Action qr -Session default
```

The dashboard is also available at `http://localhost:3000/dashboard`. Log in with the username and password from `backend-proxy/waha/.env`; do not post the password.

### Step 6 — Start the proxy and verify the safe gate

In a new PowerShell window:

```powershell
cd C:\Users\gurug\OneDrive\Desktop\gitsession\gitsession\development-projects\mobile_app\backend-proxy
.\run-proxy.ps1
```

The launcher prints the local and LAN health URLs. Verify locally:

```powershell
Invoke-RestMethod http://127.0.0.1:8081/api/health
```

Expected safe state:

- `status: "UP"`
- `provider: "waha"`
- `live_send_enabled: false`
- `live_send_blocked_reason: "kill_switch_disabled"`
- `waha.status: "connected"`
- `waha.session_status: "WORKING"`
- `waha.session_live_enabled: true`
- `waha.live_enabled: false`

If `WAHA_ENABLE_LIVE` is still `true`, stop and set it back to `false` before continuing. The proxy reads `.env` at startup, so restart it after changing the file.

### Step 7 — Generate a device JWT

In a separate PowerShell window:

```powershell
cd C:\Users\gurug\OneDrive\Desktop\gitsession\gitsession\development-projects\mobile_app\backend-proxy
.\.venv\Scripts\python.exe generate_token.py --device-id "device-test-01" --expiry-minutes 60
```

Copy the printed token into the app's **JWT Session Token** field. The token is short-lived; generate a fresh one if it expires.

### Step 8 — Build and install the Android app

From the project root:

```powershell
cd C:\Users\gurug\OneDrive\Desktop\gitsession\gitsession\development-projects\mobile_app
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug

adb devices -l
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

If `adb devices -l` is empty, connect the phone, enable **USB debugging**, and authorize the computer. Do not continue to device testing until the device appears.

### Step 9 — Configure the phone

Open the app and go to **Settings**.

Choose one network path:

**Option A — Same Wi-Fi**

1. Run `ipconfig` on the PC and find the active Wi-Fi IPv4 address.
2. Allow inbound TCP port `8081` on Windows Firewall.
3. Enter `http://<PC-LAN-IP>:8081` as the proxy URL.

**Option B — USB only**

```powershell
adb reverse tcp:8081 tcp:8081
```

Enter `http://127.0.0.1:8081` as the proxy URL.

Then:

1. Paste the device JWT.
2. Tap **Save Config**.
3. Tap **Verify Contract**.

Expected result with live sending still disabled:

**Proxy Reachable — WhatsApp Dispatch Gated**

If you see **Live dispatch ready**, the kill switch is open. Stop and set `WAHA_ENABLE_LIVE=false` unless a live send has been explicitly authorized.

### Step 10 — Safe device test with live sending disabled

1. Open **Contacts**.
2. Enable **WA consent** for one test contact. Consent is off by default.
3. Run a test call, then confirm the outcome and explicitly opt in to a follow-up.
4. Open **WhatsApp Queue** and tap **Process Queue**.
5. The app should show a gated/error result, not a fake sent state.
6. Confirm no WAHA send occurred.

A call ending must never queue or send a message automatically. An `UNKNOWN` outcome must not be retried automatically; verify in WhatsApp first, then use the app's manual retry flow with a new key.

### Step 11 — Controlled live test (only after explicit authorization)

Do this only when you have authorization to send one real message to a **user-controlled** test number.

1. Set `WAHA_ENABLE_LIVE=true` in `backend-proxy/.env`.
2. Restart the proxy.
3. Confirm health reports `live_send_enabled: true` and the WAHA session is `WORKING`.
4. Send one controlled message using a unique `jobId` / `X-Idempotency-Key`.
5. Record the WAHA message ID and `ack` value.
6. Set `WAHA_ENABLE_LIVE=false` again and restart the proxy.

A WAHA `201` means accepted by WAHA, not delivered to WhatsApp. Never reuse an idempotency key for a different message.

### Step 12 — Stop services

```powershell
# Stop the proxy with Ctrl+C in its window.

cd C:\Users\gurug\OneDrive\Desktop\gitsession\gitsession\development-projects\mobile_app\backend-proxy\waha
.\manage-waha.ps1 -Action down

# Stop keep-wsl-alive.ps1 with Ctrl+C if it is running.
```

## Test Commands

```powershell
# Backend
cd backend-proxy
.\.venv\Scripts\pytest.exe -q

# Android
cd ..
.\gradlew.bat testDebugUnitTest
```

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `WinError 10048` / port in use | Another process owns `8081` | Stop it or run `.\run-proxy.ps1` and choose another port |
| `401` from the proxy | JWT expired or invalid | Generate a new token with `generate_token.py` |
| `503` with `not_sent` | Live sending disabled, WAHA unpaired, or provider disabled | Expected during safe testing. Check health and keep the gate closed. |
| `WAHA 401` | WAHA API key mismatch | Recheck `backend-proxy/.env` and `backend-proxy/waha/.env`; restart WAHA after changing its key |
| QR expires quickly | WAHA stopped refreshing the QR | `restart-session`, then generate and scan a fresh QR |
| `adb devices -l` empty | Phone not authorized or USB debugging off | Enable USB debugging, reconnect, and authorize the PC |
| `Setup-waha-env.ps1` says `.env` exists | WAHA `.env` already generated | Do not use `-Force` unless rotating the key |
| `WAHA_ENABLE_LIVE` unexpectedly true | `setup-waha-env.ps1` writes `true` | Set it to `false` and restart the proxy |

## Documentation

- [Device Integration Readiness](docs/device-integration-readiness.md)
- [Architecture](docs/architecture.md)
- [Backend Proxy Design](docs/backend-proxy.md)
- [Message Job Idempotency](docs/message-job-idempotency.md)
- [Backend Proxy README](backend-proxy/README.md)
- [WAHA Local Setup](backend-proxy/waha/README.md)

## Secrets

`backend-proxy/.env` and `backend-proxy/waha/.env` contain server-side secrets and are git-ignored. Never paste the WAHA API key or JWT signing secret into the Android app, documentation, logs, or source control. The Android app stores only the proxy URL and a short-lived device JWT.
