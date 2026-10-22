# WAHA Gateway for the Sales Caller Backend

This directory contains the local **WAHA** WhatsApp gateway used by the FastAPI
proxy. The Android app never talks to WAHA directly, and the WAHA API key never
leaves the backend.

> **Status:** local development setup. Docker Engine runs inside **WSL 2
> Ubuntu**, not Docker Desktop. Docker Desktop requires Windows administrator
> rights/UAC and possibly a reboot, so it is intentionally not required here.

## What is included

| File | Purpose |
|---|---|
| `docker-compose.yml` | Pinned `devlikeapro/waha:noweb-2026.9.2` image, loopback-only port, named volumes, Node-based health check |
| `.env.example` | Template for WAHA API key and dashboard credentials |
| `setup-waha-env.ps1` | Generates random secrets and writes LF-terminated `.env` files without printing secrets |
| `install-docker-engine-wsl.sh` | Official Docker Engine installation for Ubuntu in WSL 2 |
| `waha.sh` | WSL-side lifecycle, health, session, and QR commands |
| `manage-waha.ps1` | Windows wrapper for `waha.sh` |
| `keep-wsl-alive.ps1` | Keeps the WSL VM running while WAHA/Docker are in use |
| `qr-*.png` | Short-lived pairing QR image; git-ignored because it contains a pairing token |

## Prerequisites

- Windows 10/11 with WSL 2 enabled and an Ubuntu 24.04 distribution.
- `systemd` enabled inside WSL (the Docker installation expects it).
- Internet access for the Docker image pull and WhatsApp pairing.
- Port **8081** for the FastAPI proxy.
- Port **3000** is bound to `127.0.0.1` inside WSL and is **not** exposed to the
  LAN. Windows reaches it through WSL localhost forwarding.

## 1. Install Docker Engine inside WSL

Docker Desktop is not used. From Windows PowerShell:

```powershell
wsl -u root -e bash /mnt/c/Users/gurug/OneDrive/Desktop/gitsession/gitsession/development-projects/mobile_app/backend-proxy/waha/install-docker-engine-wsl.sh
```

Verify the engine and Compose plugin:

```powershell
wsl -u root -e bash -lc "docker run --rm hello-world"
wsl -u root -e bash -lc "docker compose version"
```

If Docker was already installed, the script is safe to re-run; it refreshes the
official Docker apt repository and packages.

## 2. Keep WSL running

WSL 2 can shut down its VM when the last `wsl` command exits. That stops Docker
and the WAHA container. Keep a dedicated WSL process running while testing:

```powershell
.\keep-wsl-alive.ps1
```

Leave that window open. Stop it with `Ctrl+C` when you are finished with WAHA.

If you prefer a persistent setting, you can raise `vmIdleTimeout` in
`%UserProfile%\.wslconfig`, but the keep-alive script is the least invasive
option and is what this setup documents.

## 3. Generate secrets

Run the setup script once:

```powershell
.\setup-waha-env.ps1
```

It creates:

- `backend-proxy/waha/.env` with `WAHA_API_KEY`,
  `WAHA_DASHBOARD_USERNAME`, and `WAHA_DASHBOARD_PASSWORD`.
- A WAHA block in `backend-proxy/.env` with the same API key and
  `WHATSAPP_PROVIDER=waha`, `WAHA_ENABLE_LIVE=true`, and
  `WAHA_SESSION=default`.

It prints only the dashboard username and secret lengths, never the secret
values. Both `.env` files are git-ignored. The script writes **LF** line endings
because CRLF values from Windows cause malformed `X-Api-Key` headers in WSL.

If `backend-proxy/.env` does not exist yet, create it from `.env.example` first
and set a strong `JWT_SECRET`, then run the setup script again.

> **Safety note:** the setup script writes `WAHA_ENABLE_LIVE=true`. For the
> normal device-test flow, change it to `false` immediately after running the
> script and before starting the proxy. Only open the gate for an explicitly
> authorized controlled send. The root `README.md` includes the exact command.

## 4. Start WAHA and verify authentication

```powershell
.\manage-waha.ps1 -Action up
.\manage-waha.ps1 -Action wait-health
.\manage-waha.ps1 -Action health
```

Expected local evidence:

```
health_without_key_http=401
health_with_key_http=200
auth_without_key_http=401
auth_with_key_http=200
session=default {"status":"SCAN_QR_CODE", ...}
```

`/health` and `/api/*` require `X-Api-Key`. The health check is implemented with
Node because the WAHA image does not contain `wget` or `curl`. The API key stays
inside the container environment, not in the Android APK or repository.

## 5. Pair the dedicated test phone

Create/start the server-side session:

```powershell
.\manage-waha.ps1 -Action start-session -Session default
```

Then either open the dashboard:

```
http://localhost:3000/dashboard
```

Log in with the username from `backend-proxy/waha/.env` (the generated default is
`operator`) and the password from the same file. Do not paste the password into
chat, tickets, or source control.

Alternatively, generate a QR image:

```powershell
.\manage-waha.ps1 -Action qr -Session default
```

The image is saved as `backend-proxy/waha/qr-default.png`. It is short-lived
(WAHA normally allows about two minutes of QR refresh attempts). Scan it with the
dedicated test phone using **WhatsApp → Linked devices → Link a device**.

After scanning, wait for the session to become `WORKING`:

```powershell
.\manage-waha.ps1 -Action health -Session default
```

If the QR expires, WAHA marks the session `FAILED`. Restart the session and
generate a fresh QR immediately before scanning:

```powershell
.\manage-waha.ps1 -Action restart-session -Session default
.\manage-waha.ps1 -Action qr -Session default
```

`start-session` starts an existing healthy session; `restart-session` is the
correct command after a failed QR attempt.

## 6. Configure and start the proxy (safe gate closed)

The proxy reads `backend-proxy/.env`. For normal device setup and queue tests,
use the safe block below:

```dotenv
WHATSAPP_PROVIDER=waha
WAHA_ENABLE_LIVE=false
WAHA_BASE_URL=http://127.0.0.1:3000
WAHA_API_KEY=<same value as backend-proxy/waha/.env>
WAHA_SESSION=default
WAHA_VERIFY_SESSION=true
```

`WHATSAPP_PROVIDER=waha` enables health/session probing. `WAHA_ENABLE_LIVE=false`
keeps the dispatch gate closed: `POST /api/message` returns `503` with
`X-Delivery-Outcome: not_sent` and never reaches WAHA `sendText`.

> `setup-waha-env.ps1` writes `WAHA_ENABLE_LIVE=true`. Reset it to `false`
> immediately after running that script. The root `README.md` includes the exact
> PowerShell command.

Start the proxy:

```powershell
cd ..
.\run-proxy.ps1
```

The code default remains the safe `disabled` provider. Enabling live sending is a
separate, explicit operator decision and must only be done for an authorized
controlled test.

## 7. Verify the proxy and messaging gates

Health before pairing:

```powershell
Invoke-RestMethod http://127.0.0.1:8081/api/health
```

Expected fields include:

- `provider: "waha"`
- `live_send_enabled: false`
- `live_send_blocked_reason: "kill_switch_disabled"`
- `waha.status: "scan_qr"`
- `waha.live_enabled: false`
- `waha.session_status: "SCAN_QR_CODE"`
- `waakg` is retained only as a deprecated compatibility alias.

Message dispatch without a token must return `401`. With a valid JWT but an
unpaired session, it must return `503` and must not call WAHA `sendText`.

After pairing, health should show the session as connected while live sending
remains gated:

- `waha.status: "connected"`
- `waha.session_status: "WORKING"`
- `waha.session_live_enabled: true`
- `waha.live_enabled: false`
- top-level `live_send_enabled: false`

### Opening the gate for one authorized test

Only with explicit authorization, and only for a user-controlled test number:

1. Set `WAHA_ENABLE_LIVE=true` in `backend-proxy/.env`.
2. Restart the proxy.
3. Confirm health reports `live_send_enabled: true` and the session is `WORKING`.
4. Send one message with a unique `X-Idempotency-Key`; never reuse a key for a
   different message.
5. Record the WAHA message ID and `ack`.
6. Set `WAHA_ENABLE_LIVE=false` again and restart the proxy.

A WAHA `201` means WAHA accepted the request, not that WhatsApp delivered it.

## Safety rules for tests

- Negative-path tests must use an **invalid recipient**, blank content, or an
  expired token. Never use a valid recipient merely to test validation.
- A WAHA `201` means WAHA accepted the send. Record the returned message ID and
  its `ack` value. If WAHA does not return an ID, the proxy returns
  `"messageId": null`; it must not invent a fake acknowledgement ID.
- Only one real message may be sent to a **user-controlled** number, and only
  after explicit authorization. Do not reuse an idempotency key for a different
  message.
- If a send times out or returns 5xx after possible acceptance, treat the
  outcome as unknown and verify receipt before retrying.

## Error and delivery semantics

- WAHA rejects a send: the proxy returns an error and does **not** report
  success.
- WAHA times out or returns 5xx after the request may have been accepted: the
  proxy returns `X-Delivery-Owner: unknown` / `X-Delivery-Outcome: unknown` and
  caches the idempotency outcome so a retry cannot silently send a duplicate.
- Deterministic failures (for example, no WORKING session or validation errors)
  do not lock the idempotency key, so the operator can retry after fixing the
  cause.
- The Android JID `919876543210@s.whatsapp.net` is normalized by the backend to
  WAHA's `919876543210@c.us` chat ID.

## 8. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `curl: connection reset` / `000` | WAHA still starting | `.\manage-waha.ps1 -Action wait-health` |
| `health_with_key_http=400` | `.env` has CRLF and the sourced key includes `\r` | Verify the line endings first with `wsl -e file backend-proxy/waha/.env`; it must say `ASCII text`, not `with CRLF line terminators`. Rewrite it as LF, or rerun `setup-waha-env.ps1 -Force` (this **rotates the key** and requires recreating the WAHA container). |
| `401` from WAHA | Missing/incorrect `X-Api-Key` | Check the key in both `.env` files; restart the container after changing `waha/.env` |
| `session status is not as expected` | Session is `FAILED` or not `SCAN_QR_CODE` | `restart-session`, then `qr` immediately |
| QR expires quickly | WAHA ended QR refresh attempts | `restart-session` and scan within about two minutes |
| WSL/Docker/WAHA disappears | WSL VM shut down after the last command | Run `.\keep-wsl-alive.ps1` and leave it open |
| `docker: command not found` | Docker Engine is not installed in this WSL distro | Run `install-docker-engine-wsl.sh` |
| `permission denied` on `/var/run/docker.sock` | Current WSL user is not in the `docker` group | Use `wsl -u root` (the management scripts do) or add the user to the group |
| Port 3000 already in use | Another service owns the port | `Get-NetTCPConnection -LocalPort 3000`; stop that service or change the Compose/port mapping and `WAHA_BASE_URL` |
| Dashboard login fails | Wrong username/password | Read `WAHA_DASHBOARD_USERNAME` and `WAHA_DASHBOARD_PASSWORD` from `backend-proxy/waha/.env`; do not commit or paste them |

Useful commands:

```powershell
.\manage-waha.ps1 -Action status
.\manage-waha.ps1 -Action logs
.\manage-waha.ps1 -Action down
```

`logs` can contain message text and phone numbers. Treat its output as sensitive.

## Remote deployment and HTTPS

For local development, WAHA stays on `127.0.0.1:3000` and the proxy is reached
over local HTTP. For a remote or production deployment:

1. Keep WAHA bound to loopback (or a private Docker network). Never publish port
   `3000` to the internet.
2. Put the FastAPI proxy behind a TLS-terminating reverse proxy such as Caddy or
   Nginx. Use a publicly trusted certificate (for example, Let's Encrypt). A
   self-signed certificate requires explicit Android trust configuration and is
   not recommended for production.
3. Point Android at the HTTPS proxy URL only. Do not configure the WAHA URL or
   API key in the app.
4. State that the debug build's cleartext-HTTP allowance is for local testing
   only; a release build should require HTTPS.
5. Keep JWT secrets, `WAHA_API_KEY`, and dashboard credentials in server-side
   environment/secret storage. Rotate the WAHA key if it is ever exposed.
6. Keep the proxy's JWT validation, rate limiting, and idempotency in front of
   WAHA. Do not expose WAHA's generic API to handsets.

Example Caddy reverse-proxy shape (HTTPS terminates at Caddy; the proxy stays
local):

```caddyfile
proxy.example.com {
    reverse_proxy 127.0.0.1:8081
}
```

Restrict inbound firewall rules to the reverse proxy and the proxy port; do not
forward WAHA port 3000.
