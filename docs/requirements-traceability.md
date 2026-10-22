# Requirements Traceability Matrix

**Purpose**: Map each requirement from the PRD/BRD to implementation modules, design artifacts, and test cases.

---

## 1. Functional Requirements (FR)

| ID | Description | Target Module(s) | Design Artifact | Test(s) |
|----|-------------|------------------|-----------------|---------|
| **FR‑01** | User Setup & Permissions – runtime permission handling, SIM diagnostics, test call | `ui.permissions`, `telecom.calling`, `utils.PermissionHelper` | docs/architecture.md – *Permission & Diagnostics* section | Unit: PermissionHelperTest; Instrumented UI test for permission flow |
| **FR‑02** | Customer Import – CSV paste, CSV file, duplicate detection, validation, preview | `data.customer`, `ui.import` | docs/architecture.md – *Import Flow* | Unit: CsvParserTest, DuplicateDetectorTest; UI test for import screen |
| **FR‑03** | Campaign Management – CRUD, status lifecycle, stats | `data.campaign`, `ui.campaign` | docs/architecture.md – *Campaign Model* | Unit: CampaignDaoTest, CampaignStateMachineTest |
| **FR‑04** | SIM‑Based Calling – initiate cellular call via employee SIM, single active call, record events | `telecom.calling`, `callstate.Classifier` | docs/architecture.md – *Calling Engine* | Instrumented test on device for CallIntent, CallStateListenerTest |
| **FR‑05** | Call Outcome Classification – ANSWERED, NO_ANSWER, REJECTED, BUSY, FAILED, UNKNOWN | `callstate.Classifier` | docs/architecture.md – *Outcome Classification* | Unit: OutcomeClassifierTest (mocked Telephony callbacks) |
| **FR‑06** | WhatsApp Integration – WAHA session handling/pairing, secure server-side API key storage, error handling | `messaging.gateway`, `upstream.waha.WahaProvider`, `BackendProxyClient` | docs/architecture.md – *WhatsApp Gateway Adapter*; docs/backend-proxy.md | Mocked HTTP tests in `backend-proxy/tests/test_waha.py`; live health/auth/session checks with kill switch disabled; kill-switch/consent/unknown-delivery regression tests; no authorized real-delivery test performed (see docs/device-integration-readiness.md) |
| **FR‑07** | Follow‑Up Message – configurable template, job creation, opt‑out handling, explicit consent and agent opt-in | `domain.workflow.FollowUpPolicy`, `SequentialCallCoordinator`, `data.local.MessageJobEntity` | docs/message-job-idempotency.md | `FollowUpPolicyTest`, `SequentialCallCoordinatorTest` (no consent, opt-out, unknown no-auto-retry, consent-verified dispatch) |
| **FR‑08** | Asynchronous Queue & Progression – separate call & message queues, idempotent job creation, survive process kill | `queue.CallQueue`, `queue.MessageQueue`, `WorkManager` | docs/architecture.md – *Queue Orchestration* | Instrumented test for queue persistence, WorkManager test |
| **FR‑09** | Call & Message History – searchable, filterable records | `data.history`, `ui.history` | docs/architecture.md – *History Layer* | Unit: HistoryDaoTest, UI filter test |
| **FR‑10** | Dashboard – aggregate counts derived from persisted data | `ui.dashboard` | docs/architecture.md – *Dashboard UI* | UI instrumentation test verifying counts match DB |

## 2. Non‑Functional Requirements (NFR)

| ID | Description | Target Module(s) | Design Artifact | Test(s) |
|----|-------------|------------------|-----------------|---------|
| **NFR‑Reliability** | Persistent local storage, idempotency, crash recovery | `data.*`, `queue.*` | docs/architecture.md – *Persistence* | Crash‑recovery integration test |
| **NFR‑Security** | No API keys in APK, HTTPS, sanitised logs, consent/opt-out enforcement, live-send kill switch | `messaging.gateway`, `utils.SecureStorage`, `backend-proxy.main` | docs/architecture.md – *Security*; docs/device-integration-readiness.md | Security lint; APK secret scan (JWT, WAHA key, dashboard password, legacy key all absent); backend kill-switch/consent tests; debug-build only |
| **NFR‑Performance** | Import up to 5 000 rows responsive, UI updates without full restart | `ui.import`, `ui.dashboard` | docs/architecture.md – *Performance* | Profiling test, UI lag measurement |
| **NFR‑Compatibility** | Minimum Android SDK verified after call‑state validation | `build.gradle` (minSdk) | docs/architecture.md – *Compatibility* | Device matrix test report |
| **NFR‑Maintainability** | Modular structure, versioned migrations, documentation | All modules | docs/architecture.md – *Maintainability* | Code quality metrics (detekt), migration test |

---

**How to Use**: For each requirement you can locate the responsible module, refer to the corresponding design description, and find the associated automated test(s) that provide verification.
