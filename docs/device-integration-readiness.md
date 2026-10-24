# Device Integration & Run Readiness — LeadConnect

**Architecture Scope**: Local-first Android sales calling automation with dual calling modes (Safe Demo & Live SIM).

---

## 1. Readiness Summary

| Component | Status | Verification Summary |
| :--- | :--- | :--- |
| **Android Source Architecture** | **READY** | Native Kotlin, Jetpack Compose UI, Room SQLite database. |
| **Outbound Calling Module** | **READY** | Direct cellular calling via `Intent.ACTION_CALL` using phone's SIM card. |
| **Safe Demo Simulation** | **READY** | In-memory simulated calling loop for risk-free testing without carrier fees. |
| **Pre-Dial DND Suppression** | **READY** | Automated suppression check before every dial. |
| **Process Death / Crash Recovery** | **READY** | State persistence in Room SQLite prevents duplicate dialing after restart. |
| **Unit Test Suite** | **READY** | 9 unit test suites covering coordinator, policies, parsers, and state machine. |
| **External Server Dependency** | **NONE** | 100% autonomous on-device execution with zero backend servers required. |

---

## 2. Running on Device

### A. Real Android Phone (Live SIM Calling)
1. Enable **Developer Options** & **USB Debugging** on the device.
2. Connect phone via USB cable to PC.
3. Open project in **Android Studio** and click **Run ▶️** (or install via `.\gradlew.bat installDebug`).
4. Grant the requested telephony permissions (`CALL_PHONE`, `READ_PHONE_STATE`).
5. Choose **📱 LIVE SIM** mode on the Dashboard to execute sequential campaigns via your carrier.

### B. Laptop / Emulator (Safe Demo Mode)
1. Open project in **Android Studio**.
2. Launch a virtual device via **Device Manager** (e.g. Pixel 7 / API 34).
3. Click **Run ▶️**.
4. Keep the toggle on **🛡️ Safe Demo Mode** to simulate calls, progression, and outcome confirmation directly on your laptop display.
