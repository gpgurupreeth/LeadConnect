# LeadConnect — Sales Calling Automation

Local-first Android application for sequential cellular SIM sales calling, lead management, pre-dial Do-Not-Call (DND) suppression, and call outcome verification.

---

## Overview & Architecture

LeadConnect is built as an autonomous, local-first Android application that empowers sales agents to run structured calling campaigns directly from their phone with zero external server dependencies:

```text
LeadConnect/
└── app/                  Android app (Kotlin, Jetpack Compose, Room SQLite)
    └── src/
        ├── main/
        │   ├── java/com/example/mobileapp/
        │   │   ├── data/local/      (Room Database: Customers, Campaigns, CallAttempts, Suppressions, AuditEvents)
        │   │   ├── domain/
        │   │   │   ├── engine/      (CampaignEngine: execution loop, stats, state machine)
        │   │   │   ├── model/       (CallOutcome, CampaignStatus, StateMachine)
        │   │   │   └── workflow/    (SequentialCallCoordinator, CallingHoursPolicy)
        │   │   ├── telecom/         (AndroidTelecomCaller, DemoSimCaller, OutcomeClassifier)
        │   │   ├── ui/              (Jetpack Compose UI: Dashboard, Contacts, Campaign, Settings)
        │   │   └── utils/           (ContactImportParser, PhoneNumberUtils)
        │   └── res/
        └── test/                    (Unit test suite for calling engine, recovery & policies)
```

---

## Core Safety Invariants & Architecture

1. **Sequential Dialing Guarantee**:
   The engine dials leads sequentially. Each call must be verified and confirmed by the sales agent before the engine advances to the next contact.
2. **Crash & Process Death Recovery Protection**:
   If the app or device process crashes while a call is in progress, the engine logs the attempt and will **never redial** a contact whose call attempt was already initiated in that campaign.
3. **Pre-Dial DND Suppression**:
   Numbers present in the local DND suppression list are strictly blocked and audited prior to launching any telephony intent.
4. **Dual Calling Modes**:
   - **🛡️ Safe Demo Mode**: Simulates dialing locally without touching cellular radio or incurring carrier airtime charges. Perfect for training and functional testing.
   - **📱 Live SIM Mode**: Places real outbound phone calls through the device's physical SIM card using Android's Telephony APIs (`ACTION_CALL`).
5. **Permitted Calling Hours Window**:
   Enforces configurable allowable calling hours (e.g. 09:00 - 18:00) and automatically pauses the campaign if the agent attempts to dial outside the window.
6. **Listener Lifecycle Safety**:
   Telephony state observers (`TelephonyCallback` on Android 12+ / `PhoneStateListener` on older versions) are cleanly registered and unregistered to prevent memory and callback leaks.

---

## App Screens & Workflows

| Screen | Purpose |
| :--- | :--- |
| **Dashboard** | Overview of active campaign progress, calling mode toggle (Demo vs Live SIM), real-time call performance KPIs (Attempted, Answered, Unresolved, Suppressed), and live activity audit trail. |
| **Contacts** | Contact directory with CSV import parser, lead deduplication, phone normalization, DND suppression toggles, and search. |
| **Campaign** | Campaign runner and creation wizard with audience eligibility breakdown, live call controls (Start, Pause, Resume, Stop), and calling history. |
| **Settings** | Telephony permissions status & request (`CALL_PHONE`, `READ_PHONE_STATE`), default country code, calling hours window, and demo seed / database reset controls. |

---

## Setup & Building

### Prerequisites

- **JDK 17** (configured in Gradle).
- **Android SDK** (API 34, minSdk 26).
- An Android device with a working SIM card or an Android Emulator.

### Build Commands

```powershell
# Set local SDK location if needed:
# echo "sdk.dir=C:\Users\<user>\AppData\Local\Android\Sdk" > local.properties

# Build debug APK:
.\gradlew.bat assembleDebug

# Run unit test suite:
.\gradlew.bat testDebugUnitTest
```
