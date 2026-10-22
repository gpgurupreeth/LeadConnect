# Device Integration Readiness

**Date:** 2026-10-20  
**Scope:** Local Android + FastAPI proxy + WAHA development environment.  
**Safety posture:** Live WhatsApp sending remained **disabled** (`WAHA_ENABLE_LIVE=false`) for every check in this report. No calls were placed. No authorized real-delivery test was performed.

## Overall Result

| Area | Result | Summary |
|---|---|---|
| Existing project inspected and extended | **PASS** | Existing Android, backend proxy, documents, and WAHA setup were retained and extended. |
| Backend automated tests | **PASS** | `75 passed in 2.83s`. |
| Android unit tests | **PASS** | `79` tests, `0` failures, `0` errors, `0` skipped. |
| Debug APK build | **PASS** | `app/build/outputs/apk/debug/app-debug.apk`, `11,292,877` bytes. |
| APK secret scan | **PASS** | 4 server-side secret keys checked across the raw APK and all 163 ZIP entries; none found. |
| Local proxy health/auth/WAHA session | **PASS** | Health `UP`, authenticated health `200`, WAHA `connected`, session `WORKING`. |
| Kill-switch dispatch attempt | **PASS** | Returned `503`, `X-Delivery-Outcome: not_sent`, no WAHA send. |
| WAHA `sendText` count unchanged | **PASS** | Exactly one historical request remains; no new request after the readiness checks. |
| USB device discovery / APK installation | **BLOCKED** | `adb devices -l` returned no attached devices. |
| Android UI, queue, permission, and SIM-call device testing | **BLOCKED** | No device available. |
| Real authorized WhatsApp delivery | **NOT TESTED** | Not authorized during this pass. |
| Remote HTTPS deployment | **NOT TESTED** | Documented only; not exercised in this local environment. |

---

## 1. Sending-Path Audit

The only code path capable of issuing WAHA `POST /api/sendText` is:

- `backend-proxy/upstream/waha.py` → `WahaProvider.send_message()`

The dispatch endpoint `backend-proxy/main.py` opens that path only after:

1. JWT/device authentication.
2. Payload validation.
3. A required `X-Idempotency-Key` that exactly matches `jobId`.
4. Per-device rate limiting.
5. The server live-send kill switch (`WAHA_ENABLE_LIVE=true` required).
6. Explicit `consent=true` and `optOut=false`.
7. Atomic idempotency claim.

`WaaKgProvider` exists as legacy code, but `WHATSAPP_PROVIDER` accepts only `disabled` or `waha`; unknown values are rejected at configuration validation. The production dispatch path therefore cannot select the legacy adapter.

### Test-time network safety

- `backend-proxy/tests/conftest.py` forces `WHATSAPP_PROVIDER=disabled` and `WAHA_ENABLE_LIVE=false`.
- Dispatch tests replace the provider with mocks.
- `WahaProvider` refuses to construct without an `httpx.MockTransport` while `PYTEST_CURRENT_TEST` is set.
- The kill-switch regression test asserts zero WAHA HTTP requests.

---

## 2. Automated Test Evidence

### Backend — PASS

```powershell
cd backend-proxy
.\.venv\Scripts\pytest.exe -q
```

Result:

```text
75 passed in 2.83s
```

Regression coverage includes:

- `test_proxy_kill_switch_blocks_dispatch_without_any_waha_request`
- `test_proxy_requires_matching_idempotency_key_before_dispatch`
- `test_proxy_requires_consent_and_rejects_opt_out`
- `test_proxy_waha_timeout_locks_idempotency_key`
- `test_proxy_waha_5xx_locks_idempotency_key`
- `test_proxy_unexpected_exception_locks_key_as_unknown`
- `test_proxy_waha_success_replay_does_not_resend`
- `test_proxy_waha_success_without_provider_id_returns_null_message_id`
- `test_waha_disconnected_session_is_detected_before_send`
- `test_invalid_recipient_is_rejected_without_any_network_call`
- `test_waha_logs_do_not_contain_api_key_phone_or_message`
- `test_provider_defaults_to_disabled`
- `test_provider_rejects_unknown_value`

### Android — PASS

```powershell
cd mobile_app
.\gradlew.bat testDebugUnitTest
```

Result from `app/build/test-results/testDebugUnitTest/TEST-*.xml`:

```text
files=12 tests=79 failures=0 errors=0 skipped=0
```

Regression coverage includes:

- `FollowUpPolicyTest` — consent, opt-out, unknown outcome, template, and agent-opt-in rules.
- `SequentialCallCoordinatorTest` — no consent, opt-out, legacy job without consent, unknown delivery not auto-retried, idempotency preserved.
- `BackendProxyClientTest` — missing consent/opt-out rejected locally, strict key header, `UNKNOWN` on timeout, kill-switch health interpretation.
- `MessageJobIdempotencyTest` — deterministic key and fresh bounded manual-retry key.
- `CampaignAutomationAndSafeDemoTest` — call completion pauses for confirmation instead of auto-sending.
- `CampaignStateMachineTest` — pause/resume/cancel transitions.

---

## 3. APK Build and Secret Scan

| Item | Value |
|---|---|
| APK path | `C:\Users\gurug\OneDrive\Desktop\gitsession\gitsession\development-projects\mobile_app\app\build\outputs\apk\debug\app-debug.apk` |
| Size | `11,292,877 bytes` |
| SHA-256 | `C45F53AF7172AD161B865DFA2EB3FD6EB73C4D59CB82E11B6BE78E01E0FDBA1A` |
| Build type | Debug |
| ZIP entries scanned | `163` |

Secret scan checked the raw APK and every ZIP entry for UTF-8, UTF-16LE, and base64 forms of:

- `JWT_SECRET`
- `WahaProvider` API key (`WAHA_API_KEY`)
- Legacy `WAAKG_API_KEY`
- WAHA dashboard password (`WAHA_DASHBOARD_PASSWORD`)

Result: **no value found** for any checked key. No `.env`-like file and no `backend-proxy`/WAHA configuration file was present in the APK.

> The APK is a debug artifact. It is not production-signed and should not be distributed as a release.

---

## 4. Local Health, Authentication, and Session Evidence

The proxy was started with `backend-proxy\run-proxy.ps1` on `127.0.0.1:8081`. WAHA remained bound to `127.0.0.1:3000` inside WSL.

| Check | Result |
|---|---|
| Unauthenticated health | `200`, `auth.authenticated=false` |
| Authenticated health | `200`, `auth.authenticated=true`, device ID returned |
| Top-level proxy status | `UP` / `running` |
| Provider | `waha` |
| Top-level `live_send_enabled` | `false` |
| Top-level blocked reason | `kill_switch_disabled` |
| WAHA configured | `true` |
| WAHA status | `connected` |
| WAHA session status | `WORKING` |
| WAHA `session_live_enabled` | `true` |
| WAHA `live_enabled` | `false` |

This proves the intended split: the WAHA session is healthy and paired, but dispatch remains closed by the proxy kill switch.

Proxy log check: the 24-line log contained none of the four checked secret values, no Bearer/JWT token pattern, no `sendText`, no traceback, no `CRITICAL`, and no HTTP `500`. The only message-related entry was the expected `POST /api/message ... 503 Service Unavailable`.

---

## 5. Dispatch-While-Disabled Evidence

A safe authenticated dispatch was attempted with a valid job/key pair but deliberately **without consent**:

```text
jobId = verify-disabled-gate-20261020
X-Idempotency-Key = verify-disabled-gate-20261020
consent = false
optOut = false
```

Result:

```text
HTTP 503
X-Delivery-Outcome: not_sent
X-Live-Send-Enabled: false
detail: Live WhatsApp sending is disabled by the server kill switch
        (WAHA_ENABLE_LIVE=false). No message was sent.
```

The no-consent payload was chosen so that, even if the kill switch were bypassed, the next guard would still block the request. The request returned `503` from the earlier kill-switch guard, proving that guard executes before consent and before any provider call.

The fully consented kill-switch path is covered by the backend regression test `test_proxy_kill_switch_blocks_dispatch_without_any_waha_request`, which asserts zero WAHA requests.

---

## 6. WAHA `sendText` Count

WAHA logs were parsed without printing message bodies or credentials.

| Metric | Value |
|---|---|
| `POST /api/sendText` requests | `1` |
| Request time | `17:01:18.307Z` |
| HTTP status | `201` |
| New sends during readiness checks | `0` |

The single historical request is the earlier unintended local-validation send:

- Recipient: test number `919876543210`
- Text: `Local validation test; must not be sent`
- WAHA message ID: `true_919876543210@c.us_3EB0530C3F47CEE74FC1B9`
- `ack = 0`

**HTTP 201 means WAHA accepted the request, not that WhatsApp delivered it.** Delivery is not confirmed. No additional WhatsApp send was authorized or initiated; the blocked dispatch attempt above never reached WAHA.

---

## 7. Requirement Evidence Matrix

| Requirement | Status | Evidence |
|---|---|---|
| Extend the existing project; do not rebuild | **PASS** | Existing Android/backend/WAHA structure retained and modified. |
| Do not send WhatsApp messages or place calls during this pass | **PASS** | No calls; no new WAHA send; live switch disabled. |
| Identify every WAHA `sendText` path | **PASS** | Only `WahaProvider.send_message`; provider selection rejects legacy/unknown providers. |
| Server-side live-send kill switch defaults off | **PASS** | `WAHA_ENABLE_LIVE` default `false`; live health reports `kill_switch_disabled`. |
| Reject dispatch without a WAHA send request | **PASS** | Live `503`; backend test asserts zero WAHA requests. |
| Require valid idempotency key; no `jobId` substitution | **PASS** | Header required and must equal `jobId`; `test_proxy_requires_matching_idempotency_key_before_dispatch`. |
| Validate key, payload, recipient, auth, consent, opt-out | **PASS** | Backend tests plus Android local guards; invalid recipient rejected without network. |
| Timeout/5xx/unexpected post-error = unknown | **PASS** | `UNKNOWN` outcome, key locked; timeout/5xx/exception tests. |
| Retries cannot silently duplicate | **PASS** | Replay test, unknown lock, manual retry requires new key. |
| Test commands cannot real-send by default | **PASS** | Disabled provider, mocked transports, `PYTEST_CURRENT_TEST` constructor guard. |
| Regression tests for all required cases | **PASS** | Missing key, invalid payload, duplicate, timeout, disabled live, attempted dispatch, consent, opt-out, unknown. |
| WAHA send count does not increase | **PASS** | Exactly one historical request; zero new. |
| Health/auth/session/config/logs/no secrets | **PASS** | Health/auth/session live checks; sanitized logs; APK scan clean. |
| Keep WAHA and FastAPI local | **PASS** | WAHA `127.0.0.1:3000`; proxy is a local development service on LAN port `8081`. |
| Configurable proxy URL/token storage | **PASS (code/build)** | Settings path retained; APK builds. Device UI not exercised. |
| Accurate gateway status/errors | **PASS (unit/live health)** | Health parser tests plus live health response. |
| Queue states pending/sending/sent/accepted/failed/unknown | **PASS (code/build)** | Enum, Room v2, queue screen counts, unit tests. Device UI not exercised. |
| Nullable provider IDs | **PASS** | Provider and proxy tests return `null` rather than fabricated IDs. |
| Safe retry controls | **PASS (unit/build)** | `UNKNOWN` blocks automatic retry; manual retry uses a new bounded key. |
| Pause/resume/stop | **PASS (unit/build)** | Campaign engine controls and state-machine tests. Device UI not exercised. |
| Manual unknown-outcome confirmation | **PASS (code/build)** | Verification dialog and new-key retry path. Device UI not exercised. |
| Consent/opt-out eligibility | **PASS** | Policy, coordinator, proxy, and Android client tests. |
| Separate SIM calling from WhatsApp dispatch | **PASS (unit/build)** | Dispatch only after explicit confirmation; call completion awaits agent input. No device SIM test. |
| No automatic follow-up because a call ended | **PASS** | `AwaitingConfirmation` test and `FollowUpPolicy` default-off agent opt-in. |
| Backend and Android tests, APK, APK scan, `adb` check | **PASS / BLOCKED** | Tests and build pass; `adb` has no device. |
| README/technical documentation updated | **PASS** | Backend README, architecture, proxy, idempotency, traceability, and this evidence document. |
| Preserve unrelated changes; no commit/push | **PASS** | No commit or push performed. |

---

## 8. Blocked and Not-Tested Items

### BLOCKED — no USB device

```text
$ adb devices -l
List of devices attached
```

No device is attached, so the following cannot be verified:

- APK installation and launch.
- Runtime permission prompts.
- Settings URL/token save and contract verification on the handset.
- Contacts consent toggle UI.
- WhatsApp queue status rendering and retry dialogs.
- Actual SIM call initiation and call-state classification.
- `adb reverse tcp:8081 tcp:8081` fallback.
- Process-death/restart behavior on a physical phone.

### NOT TESTED — real delivery

No authorized real WhatsApp message was sent. WAHA `201` from the historical request is not delivery proof; `ack=0` means no delivery acknowledgement was observed.

### NOT TESTED — remote deployment

HTTPS reverse-proxy deployment, certificate validation, and remote firewall rules are documented but were not exercised in this local pass.

---

## 9. Exact Next Device Action

1. Connect an Android phone with USB debugging enabled and authorize the computer.
2. Confirm discovery:
   ```powershell
   adb devices -l
   ```
3. Install the debug APK:
   ```powershell
   adb install -r "C:\Users\gurug\OneDrive\Desktop\gitsession\gitsession\development-projects\mobile_app\app\build\outputs\apk\debug\app-debug.apk"
   ```
4. Start the proxy and generate a token:
   ```powershell
   cd backend-proxy
   .\run-proxy.ps1
   .\.venv\Scripts\python.exe generate_token.py --device-id "device-test-01" --expiry-minutes 60
   ```
5. In the app **Settings**, enter either the PC LAN URL (`http://<PC-IP>:8081`) or use:
   ```powershell
   adb reverse tcp:8081 tcp:8081
   ```
   and enter `http://127.0.0.1:8081`. Save the token and tap **Verify Contract**.
6. In **Contacts**, enable **WA** consent for one test contact.
7. Run the campaign/queue with **live sending still disabled**. Confirm that:
   - No call is auto-followed by a message without confirmation.
   - A queued job shows the gate/error rather than a fake sent state.
   - `POST /api/message` returns `503` and no WAHA send occurs.
8. Only after explicit authorization, change `WAHA_ENABLE_LIVE=true`, restart the proxy, send one controlled test, check WAHA logs/ack, then set it back to `false`.
9. Re-check the WAHA `POST /api/sendText` count after every test.

---

## 10. Local Configuration Notes

- Backend proxy: `http://127.0.0.1:8081` (launcher also prints LAN URLs).
- WAHA container: `sales-caller-waha`, image `devlikeapro/waha:noweb-2026.9.2`, healthy.
- WAHA bind: `127.0.0.1:3000` inside WSL; not LAN/public.
- `WHATSAPP_PROVIDER=waha`, `WAHA_ENABLE_LIVE=false`, `WAHA_SESSION=default`.
- `backend-proxy/.env` and `backend-proxy/waha/.env` are git-ignored. Their values were read only for presence checks and secret scanning; no secret value is reproduced in this document or logs.

## 11. Repository State

### Final repository checks

| Check | Result |
|---|---|
| Source/docs scan for the 4 server-side secret values | **0 hits** (`.env` files excluded from the scan) |
| Relative Markdown links in README/docs | **0 broken** across 11 checked files |
| Proxy port `8081` after shutdown | **Closed** |
| WAHA `POST /api/sendText` count | **1 historical**, no new request |
| `adb devices -l` | **No devices attached** |

- **No commit and no push were performed.**
- The parent repository is on branch `gp`. It contains unrelated pre-existing changes under `youtubeclone/`, `clg/`, root images, and other unrelated folders. Those were not modified or committed.
- `mobile_app/` remains untracked in the parent repository, so Git cannot produce a normal per-file diff for it.

Primary files created or changed for this readiness work:

- **Backend proxy:** `backend-proxy/main.py`, `config.py`, `upstream/waha.py`, `upstream/base.py`, `upstream/disabled.py`, `upstream/__init__.py`, `upstream/waakg.py`, `tests/conftest.py`, `tests/test_waha.py`, `tests/test_message.py`, `tests/test_idempotency.py`, `tests/test_health.py`, `README.md`.
- **Android:** `AppViewModel.kt`, `BackendProxyClient.kt`, `SequentialCallCoordinator.kt`, `FollowUpPolicy.kt`, `CampaignEngine.kt`, `IdempotencyUtils.kt`, `Enums.kt`, `Entities.kt`, `Daos.kt`, `AppDatabase.kt`, `ContactsScreen.kt`, `WhatsAppQueueScreen.kt`, `OutcomeConfirmationDialog.kt`, `SettingsScreen.kt`, `SecureStorage.kt`, `IMessagingProvider.kt`, `WaaKgStubProvider.kt`.
- **Android tests:** `SequentialCallCoordinatorTest.kt`, `FollowUpPolicyTest.kt`, `BackendProxyClientTest.kt`, `RecoveryAndIdempotencyTest.kt`, `DatabaseEntitiesTest.kt`, `MessageJobIdempotencyTest.kt`, `CampaignAutomationAndSafeDemoTest.kt`.
- **Documentation:** root `README.md`, `docs/architecture.md`, `docs/backend-proxy.md`, `docs/message-job-idempotency.md`, `docs/requirements-traceability.md`, `docs/device-integration-readiness.md` (new), `backend-proxy/README.md`.

The debug build outputs and generated test/XML artifacts are build products and are not part of the source change set.
