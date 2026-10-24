# Requirements Traceability Matrix — LeadConnect

**Purpose**: Map each calling automation requirement to its implementation modules, design artifacts, and test cases.

---

## 1. Functional Requirements (FR)

| ID | Description | Target Module(s) | Design Artifact | Test(s) |
| :--- | :--- | :--- | :--- | :--- |
| **FR‑01** | **User Setup & Permissions** – Telephony runtime permission handling (`CALL_PHONE`, `READ_PHONE_STATE`), SIM checks | `telecom.AndroidTelecomCaller`, `ui.SettingsScreen` | docs/diagnostics.md | Unit & Mock Telecom tests |
| **FR‑02** | **Customer Import & Lead Management** – CSV file parsing, text paste, normalization, duplicate detection, DND tagging | `utils.ContactImportParser`, `utils.PhoneNumberUtils`, `ui.ContactsScreen` | docs/architecture.md | `ContactImportParserTest`, `PhoneNumberUtilsTest` |
| **FR‑03** | **Campaign Management** – Create campaigns, audience calculation, lifecycle state machine (Start, Pause, Resume, Stop) | `domain.model.CampaignStateMachine`, `domain.engine.CampaignEngine`, `ui.CampaignScreen` | docs/architecture.md | `CampaignStateMachineTest`, `CampaignAutomationAndSafeDemoTest` |
| **FR‑04** | **SIM‑Based Outbound Calling** – Initiate real cellular calls via device SIM with telephony state tracking | `telecom.AndroidTelecomCaller`, `telecom.ICaller` | docs/architecture.md | `AndroidTelecomCaller` tests |
| **FR‑05** | **Safe Demo Simulation** – Local call simulation without carrier radio or charges for training/testing | `telecom.DemoSimCaller` | docs/architecture.md | `CampaignAutomationAndSafeDemoTest` |
| **FR‑06** | **Call Outcome Classification & Verification** – Classify states and require agent confirmation for accurate tracking | `telecom.OutcomeClassifier`, `ui.OutcomeConfirmationDialog` | docs/call-outcome-classifier.md | `OutcomeClassifierTest` |
| **FR‑07** | **Sequential Calling Loop & Progression** – Guarantees one call at a time; stops and prompts agent before proceeding | `domain.workflow.SequentialCallCoordinator` | docs/architecture.md | `SequentialCallCoordinatorTest` |
| **FR‑08** | **Pre-Dial DND Suppression** – Strict skip and audit logging for suppressed/opt-out phone numbers | `domain.workflow.SequentialCallCoordinator`, `data.local.dao.SuppressionDao` | docs/configuration.md | `SequentialCallCoordinatorTest` |
| **FR‑09** | **Permitted Calling Hours Window** – Enforce business calling windows (e.g., 09:00 - 18:00) | `domain.workflow.CallingHoursPolicy` | docs/configuration.md | `CallingHoursPolicyTest` |
| **FR‑10** | **Crash Recovery & Idempotency** – Prevent duplicate dialing after unexpected process death | `domain.workflow.SequentialCallCoordinator` | docs/architecture.md | `RecoveryAndIdempotencyTest` |
| **FR‑11** | **Dashboard & KPI Analytics** – Aggregated KPIs (Attempted, Answered, Unresolved, Suppressed) and audit trail | `ui.DashboardScreen`, `domain.engine.CampaignEngine` | docs/architecture.md | `CampaignAutomationAndSafeDemoTest` |

---

## 2. Non‑Functional Requirements (NFR)

| ID | Description | Target Module(s) | Design Artifact | Test(s) |
| :--- | :--- | :--- | :--- | :--- |
| **NFR‑Reliability** | Local SQLite persistence, crash resilience, zero data loss | `data.local.AppDatabase`, `data.local.dao.*` | docs/architecture.md | `DatabaseEntitiesTest` |
| **NFR‑Privacy & Safety** | 100% on-device processing, no external servers, strict DND enforcement | `data.local.*`, `telecom.*` | docs/configuration.md | `SequentialCallCoordinatorTest` |
| **NFR‑Performance** | Responsive Compose UI, background Coroutines for all database and telephony queries | `ui.*`, `domain.engine.*` | docs/architecture.md | Lifecycle & UI tests |
