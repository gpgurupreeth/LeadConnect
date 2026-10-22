# Configuration Design

## Purpose
Define the runtime configuration file (`config.json`) stored in the app‑private `filesDir`. It controls policy flags, retry behavior, and opt‑out handling without requiring a new app build.

## File Location & Format
* **Path:** `context.filesDir/config.json`
* **Format:** JSON (pretty‑printed).

```json
{
  "followUpPolicy": {
    "eligibleOutcomes": ["NO_ANSWER", "REJECTED"],
    "allowBusy": false,
    "allowFailed": false
  },
  "retryPolicy": {
    "maxAttempts": 3,
    "initialBackoffMs": 2000,
    "backoffMultiplier": 2.0
  },
  "optOut": {
    "enabled": true,
    "storage": "suppression_table"
  },
  "testCallNumber": "+18005551234",
  "logLevel": "INFO"
}
```

## Fields Explanation
| Field | Description | Allowed Values |
|-------|-------------|----------------|
| `followUpPolicy.eligibleOutcomes` | Outcomes that trigger a WhatsApp follow‑up. Must match the `CallOutcome` enum. | Array of strings (`ANSWERED`, `NO_ANSWER`, `REJECTED`, `BUSY`, `FAILED`, `UNKNOWN`). |
| `followUpPolicy.allowBusy` / `allowFailed` | Additional outcomes that may be enabled by a business decision. | Boolean |
| `retryPolicy.maxAttempts` | Maximum number of submission attempts for a `MessageJob`. | Integer ≥ 1 |
| `retryPolicy.initialBackoffMs` | Wait time before first retry (ms). | Positive integer |
| `retryPolicy.backoffMultiplier` | Exponential back‑off factor. | Float > 1 |
| `optOut.enabled` | Whether the opt‑out list is enforced. | Boolean |
| `optOut.storage` | Where opt‑out entries are persisted. Currently only `suppression_table` is supported. |
| `testCallNumber` | Default number used by the Diagnostics screen for the test call. | E.164 formatted string |
| `logLevel` | Verbosity of internal logging. | `DEBUG`, `INFO`, `WARN`, `ERROR` |

## Loading & Validation
```kotlin
class ConfigProvider @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val configFile = File(ctx.filesDir, "config.json")
    private val gson = Gson()
    lateinit var config: Config
        private set

    fun load() {
        if (!configFile.exists()) writeDefault()
        config = gson.fromJson(configFile.readText(), Config::class.java)
        validate()
    }

    private fun writeDefault() {
        configFile.writeText(gson.toJson(defaultConfig()))
    }

    private fun validate() {
        // ensure eligibleOutcomes contain only known enums, backoff values are positive, etc.
    }
}
```
`Config` is a data class mirroring the JSON structure.

## Runtime Usage Examples
* **Message Scheduler** reads `retryPolicy` to configure WorkManager back‑off settings.
* **Follow‑Up Policy** is consulted by `MessagingEngine` before creating a `MessageJob`.
* **Opt‑Out** check is performed in `MessageJobRepository` before enqueuing a job.

## Updating Configuration
* The app includes a **Settings** screen where advanced users can edit the JSON (via a text editor view) and press **Save**.
* Changes take effect immediately after `ConfigProvider.load()` is called (e.g., on app resume).

## Acceptance Criteria
1. `config.json` is created on first launch with the default content shown above.
2. Changing `followUpPolicy.eligibleOutcomes` instantly alters which call outcomes generate a follow‑up.
3. Invalid JSON or out‑of‑range values cause the app to revert to defaults and log a warning.
4. Unit tests verify loading, default creation, and validation logic (`ConfigProviderTest`).

## References
* PRD Section **FR‑05** – Call Outcome Classification.
* PRD Section **FR‑07** – Follow‑Up Message policy.
* NFR‑Security – Configuration file lives in internal storage, not exposed to other apps.
