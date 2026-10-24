# Architecture Overview — LeadConnect

Local-first Android application designed for sales calling automation, contact management, sequential dialing, and outcome tracking.

---

## High‑Level Modules

| Module | Responsibility | Key Interfaces / Classes | Dependencies |
| :--- | :--- | :--- | :--- |
| **UI Layer** | Modern Jetpack Compose screens: Dashboard, Contacts, Campaign runner, Settings, and Outcome dialogs. | `MainApp`, `DashboardScreen`, `CampaignScreen`, `ContactsScreen`, `SettingsScreen`, `OutcomeConfirmationDialog` | AndroidX, Jetpack Compose, Material3 |
| **ViewModel** | Coordinates UI state, data flows, calling engine triggers, and customer imports. | `AppViewModel` | AndroidX Lifecycle, ViewModel, Coroutines Flow |
| **Calling Engine** | Manages campaign state machine (Start, Pause, Resume, Stop) and coordinates sequential calling loops. | `CampaignEngine`, `CampaignStateMachine`, `CallingHoursPolicy` | Kotlin Coroutines |
| **Telephony & Providers** | Interfaces with cellular SIM hardware or simulates calls for demo testing. | `ICaller`, `AndroidTelecomCaller`, `DemoSimCaller`, `OutcomeClassifier` | Android Telephony APIs (`ACTION_CALL`, `TelephonyCallback`) |
| **Sequential Coordinator** | Guarantees single active call, pre-dial DND suppression, crash recovery (no redialing), and outcome verification. | `SequentialCallCoordinator`, `CallStepResult` | Room DAOs |
| **Local Persistence** | Local-first Room SQLite storage: customers, campaigns, call attempts, DND suppressions, and audit trails. | `AppDatabase`, `CustomerDao`, `CampaignDao`, `CallAttemptDao`, `SuppressionDao`, `AuditEventDao` | AndroidX Room, SQLite |
| **Utilities** | Phone number normalization and CSV contact import parser with duplicate detection. | `PhoneNumberUtils`, `ContactImportParser` | Standard Library |

---

## Layered Architecture

```text
Presentation (Jetpack Compose UI)
       ↓
ViewModel (AppViewModel)
       ↓
Domain & Workflow (CampaignEngine, SequentialCallCoordinator, CallingHoursPolicy)
       ↓
Telecom Layer (AndroidTelecomCaller / DemoSimCaller) & Persistence (Room SQLite DAOs)
```

1. **Zero External Server Dependency**: 100% of data, states, and campaign runs live on the local device.
2. **Dual-Mode Calling Architecture**:
   - **Safe Demo Mode**: Simulates dialing and durations in memory without carrier charges.
   - **Live SIM Mode**: Dials real cellular numbers via the phone's physical SIM card.

---

## Key Design Documents
- [Call Outcome Classification Design](call-outcome-classifier.md)
- [Diagnostics & Telephony Permissions Design](diagnostics.md)
- [Configuration & Calling Hours Design](configuration.md)
- [Requirements Traceability Matrix](requirements-traceability.md)
