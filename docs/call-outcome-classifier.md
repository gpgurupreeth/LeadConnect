# Call Outcome Classification Design

## Goal
Map raw Android telephony callbacks to the six logical outcomes defined in the PRD:
`ANSWERED`, `NO_ANSWER`, `REJECTED`, `BUSY`, `FAILED`, `UNKNOWN`.

## Limitations of Android Telephony Callbacks
* `PhoneStateListener` provides only `CALL_STATE_IDLE`, `CALL_STATE_OFFHOOK`, `CALL_STATE_RINGING`.
* It does **not** expose explicit “busy” or “rejected” signals; those must be inferred from the sequence and timing of callbacks.
* Call duration alone is unreliable (network latency, carrier‑specific behaviour). Therefore we **do not** use duration as the sole classifier.
* On many devices the `CALL_STATE_RINGING` event never arrives for outgoing calls, making some outcomes impossible to detect.

## Classification Heuristic (derived from PoC logs)
| Observed Callback Sequence | Inferred Outcome | Confidence |
|----------------------------|------------------|------------|
| `RINGING → OFFHOOK → IDLE` | **ANSWERED** | High |
| `OFFHOOK → IDLE` (no `RINGING`) | **NO_ANSWER** (if call lasted < 5 s) | Medium |
| `RINGING → IDLE` (no `OFFHOOK`) | **REJECTED** (callee hung up before pickup) | Medium |
| `OFFHOOK → IDLE` with error broadcast `android.intent.action.PHONE_STATE` error code `BUSY` (if available) | **BUSY** | Low |
| Any unexpected error code from `TelephonyManager` (e.g., `CALL_STATE_ERROR`) | **FAILED** | Low |
| Any sequence not matching the above or missing callbacks | **UNKNOWN** | — |

### Fallback to Manual Confirmation
When the classifier returns `UNKNOWN` (or any low‑confidence outcome), the UI must present a **Manual Confirmation Dialog** allowing the salesperson to select the correct outcome before the system proceeds.

## Implementation Sketch
```kotlin
class OutcomeClassifier {
    fun classify(events: List<CallStateEvent>): CallOutcome {
        // pattern‑match the sequence, apply timing thresholds, return UNKNOWN if no match
    }
}

data class CallStateEvent(val state: Int, val timestamp: Long)

enum class CallOutcome { ANSWERED, NO_ANSWER, REJECTED, BUSY, FAILED, UNKNOWN }
```

## Acceptance Criteria
1. All six outcomes are representable in the data model (`CallAttempt.classifiedOutcome`).
2. Unit tests (`OutcomeClassifierTest`) cover every pattern listed above using mock event streams.
3. Instrumented device test runs the PoC on a physical device and verifies that the classifier matches the observed ground truth for each scenario.
4. When the classifier returns `UNKNOWN`, the UI forces manual selection before queue progression.

## References
* Android TelephonyManager documentation – `PhoneStateListener` callbacks.
* PoC logs captured in Phase 4 (see internal test documentation).
* PRD Section **FR‑05** – Call Outcome Classification.
