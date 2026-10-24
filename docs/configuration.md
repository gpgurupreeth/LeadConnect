# Configuration & Calling Policies Design

## Purpose
LeadConnect manages local-first operational settings and policies directly inside the application, governing dialing parameters, permitted calling hours windows, and Do-Not-Call (DND) suppression.

---

## Operational Settings

| Setting | Purpose | Default Value | Configurable In |
| :--- | :--- | :--- | :--- |
| **Calling Mode** | Switches between `Safe Demo Mode` (simulated calls) and `Live SIM Mode` (real carrier calls). | `Safe Demo Mode` | Dashboard / Settings Screen |
| **Permitted Calling Hours Window** | Restricts outbound campaign dialing to allowable business hours (e.g. 09:00 to 18:00) to prevent late-night calling. | `09:00 – 18:00` | Settings Screen |
| **Enforce Calling Hours** | If enabled, the campaign runner automatically pauses and logs when an attempt falls outside hours. | `true` | Settings Screen |
| **Default Country Code** | Automatically prefixes raw numbers without international codes. | `+91` | Settings Screen |
| **DND Suppression Enforcement** | Cross-checks phone numbers against the local suppression table prior to any dialing. | `Always Enforced` | System Invariant |

---

## Safety & Invariant Guarantees

1. **Sequential Dialing**:
   - Only one lead is dialed at a time. The engine halts until the current call ends and its outcome is verified.
2. **Pre-Dial DND Suppression**:
   - Numbers marked in the local suppression list are strictly skipped with audit logging.
3. **Crash Recovery Protection**:
   - If the application or device crashes while a campaign is running, contacts with initiated attempts in that campaign are marked and **never redialed**.
4. **Agent Outcome Verification**:
   - Call outcomes (`ANSWERED`, `BUSY`, `NO_ANSWER`, `REJECTED`, `UNKNOWN`) are recorded with user confirmation and optional notes before advancing.
