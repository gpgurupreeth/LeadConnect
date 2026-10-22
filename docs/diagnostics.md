# Diagnostics Design

## Purpose
Provide a UI and supporting ViewModel that lets a sales employee verify that the device is capable of performing SIM‑based outbound calls and that all required permissions are granted.

## Permissions
| Permission | Reason | Runtime request? |
|------------|--------|------------------|
| `android.permission.CALL_PHONE` | Required to place a cellular call via `Intent.ACTION_CALL`. | Yes |
| `android.permission.READ_PHONE_STATE` | Needed to query SIM information and listen to `PhoneStateListener`. | Yes |
| `android.permission.READ_CONTACTS` (optional) | Allows importing contacts directly from the device address book. | No (only if feature added). |

## SIM Selection
* Query `TelecomManager.getCallCapablePhoneAccounts()` to obtain a list of available SIMs.
* Present the list in a **Spinner** UI; persist the selected `PhoneAccountHandle` in `SecureStorage`.
* If only a single SIM is present, auto‑select it.

## Diagnostic Screen
* Shows Android version, API level, and whether the device reports `TelecomManager.isVoiceCapable`.
* Displays the list of detected SIMs (carrier name, slot index, ICC ID).
* Provides a **Test Call** button that dials a configurable test number (default `+18005551234`).
* After the test call ends, the `CallStateObserver` logs the raw telephony callbacks and displays them for the user.

## Acceptance Criteria
1. All required permissions are requested with a clear rationale UI.
2. The screen correctly lists zero, one, or multiple SIMs.
3. Pressing **Test Call** launches the dialer, and after the call the raw callback sequence is shown.
4. If any permission is denied, the screen shows a recovery path (Open Settings button).

## References
* Android Telecom documentation – `PhoneAccountHandle`, `CallCapablePhoneAccounts`.
* `PermissionHelper` utility (to be implemented).
