# Architecture Overview

## High‑Level Modules

| Module | Responsibility | Key Interfaces | Dependencies |
|--------|----------------|----------------|--------------|
| **UI Layer** | Jetpack Compose screens, navigation, user interactions | `ViewModel` contracts, `Navigator` | AndroidX, Compose, ViewModel, Navigation |
| **Customer Management** | Import, validation, deduplication, persistence of `Customer` entities | `CustomerRepository`, `ImportService` | Room, CSV parsing libs |
| **Campaign Management** | CRUD of campaigns, state machine, progress statistics | `CampaignRepository`, `CampaignStateMachine` | Room |
| **Calling Engine** | Initiates cellular calls via `Intent.ACTION_CALL`, listens to `TelecomManager`/`PhoneStateListener` | `CallDispatcher`, `CallStateObserver` | Android Telecom APIs, PermissionHelper |
| **Call‑State Classification** | Maps raw telephony callbacks to outcome enums (ANSWERED, NO_ANSWER, …) | `OutcomeClassifier` | Android Telephony, internal heuristics |
| **Queue Orchestration** | Drives sequential processing of call and message queues, ensures single‑active call | `CallQueue`, `MessageQueue`, `WorkManager` jobs | WorkManager, CallDispatcher, MessagingEngine |
| **Messaging Engine** | Creates `MessageJob`s only after explicit agent opt-in, consent, and opt-out checks; tracks `PENDING/SENDING/SENT/FAILED/UNKNOWN`; manual verification for unknown outcomes | `MessageJobRepository`, `MessageScheduler`, `FollowUpPolicy` | Backend Proxy Client, SecureStorage |
| **WhatsApp Gateway Adapter** | Backend-side WAHA adapter: `sendText`, JID normalization, session preflight, health, kill switch, error mapping | `upstream.waha.WahaProvider`, `backend-proxy.main` | FastAPI, httpx, WAHA Docker |
| **Persistence** | Room database with entities: Customer, Campaign, CallAttempt, MessageJob, AuditEvent; version 2 adds consent and nullable provider message ID | DAO interfaces | Room |
| **Security & Config** | Holds the proxy URL and short-lived JWT session token on-device; server keeps the WAHA key. Tracks opt-outs and per-contact WhatsApp consent. | `SecureStorage`, `ConfigProvider`, `CustomerEntity.consentGiven` | Android Keystore |
| **Reporting & Diagnostics** | Dashboard aggregates, filterable history, diagnostic screens | `DashboardViewModel`, `HistoryRepository` | Persistence, Queue stats |

## Layered Architecture

```
Presentation (Compose UI) → ViewModel → Use‑Cases/Interactors → Repositories → Data Sources (Room, Network)
```

- All business logic lives in *use‑case* classes, making it testable.
- Network layer is isolated behind the `MessagingEngine` interface, enabling future provider swaps.
- Calling abstraction (`ICaller`) permits future SIP/cloud providers.

## Extensibility Points

1. **CallingProvider** – implements `ICaller`. Current implementation uses Android `Intent`.
2. **MessagingProvider** – implements `IMessagingProvider`. Android dispatches through `BackendProxyClient`; the backend uses `upstream.waha.WahaProvider`.
3. **Database Migration** – versioned Room migrations allow schema evolution.
4. **Configuration** – JSON/YAML file in app‑private storage for policy flags (e.g., follow‑up eligibility).

---

*Documented for Phase 1 – any changes will be reflected in subsequent design artifacts.*

### Design Documents
- [Diagnostics Design](diagnostics.md)
- [Call Outcome Classification Design](call-outcome-classifier.md)
- [Backend Proxy Design](backend-proxy.md)
- [Message Job Idempotency Design](message-job-idempotency.md)
- [Configuration Design](configuration.md)
- [Device Integration Readiness](device-integration-readiness.md)
