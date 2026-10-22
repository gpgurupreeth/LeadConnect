# Message Job Idempotency Design

## Purpose
Define a deterministic identifier for each logical follow‑up message so that retries or duplicate submissions never create multiple WAHA sends.

## Idempotency Key Generation
* **Inputs** – `campaignId`, `customerId`, `callAttemptId`. There is no timestamp input, so retrying the same logical follow-up produces the same key.
* **Algorithm** – Compute `SHA-256` of:
```
"msg_c${campaignId}_u${customerId}_a${attemptId}"
```
* Encode the resulting hash as a lower-case hex string (64 characters). This becomes `MessageJobEntity.jobId` and is sent as the `X-Idempotency-Key` header.
* On a definitive retry, the same inputs produce the identical key. The backend replays the cached success instead of sending a second message.
* After an `UNKNOWN` outcome, `IdempotencyUtils.generateManualRetryJobId` creates a new bounded key only after the agent confirms the message is absent.

## Database Schema (Room, version 2)
```kotlin
@Entity(
    tableName = "message_jobs",
    indices = [Index(value = ["jobId"], unique = true), Index(value = ["status"])]
)
data class MessageJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: String,                    // idempotency key sent to proxy
    val campaignId: Long,
    val customerId: Long,
    val phoneNumber: String,
    val templateName: String,
    val parametersJson: String,
    val status: String,                   // PENDING/SENDING/SENT/FAILED/UNKNOWN
    val retryCount: Int = 0,
    val nextRetryTime: Long? = null,
    val lastError: String? = null,
    val consentVerified: Boolean = false,
    val providerMessageId: String? = null, // nullable; never fabricated
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

## Failure & Retry Scenarios
| Scenario | Behavior |
|----------|----------|
| **Process death after the job is claimed** | The job is persisted as `SENDING` before the network call. Automatic dispatch selects only `PENDING`, so a crash does not silently re-send it. |
| **Definitive network/provider failure (`NOT_SENT`)** | The job returns to `PENDING` with the same key while `retryCount < 3`; after three attempts it becomes `FAILED`. |
| **Duplicate request from UI** | The unique `jobId` index rejects a duplicate row; no new job is created. |
| **Permanent proxy error (400/403/422)** | Job status becomes `FAILED`; no automatic retry. |
| **Missing consent or opt-out** | The job is not queued, or a legacy queued job becomes `FAILED`; no provider call. |
| **Timeout, 5xx, or unexpected post-dispatch error** | The proxy returns `X-Delivery-Outcome: unknown` and locks the key. Android marks the job `UNKNOWN`; automatic retry is disabled. |
| **Manual retry after `UNKNOWN`** | The agent must verify in WhatsApp first. The app then creates a new job key via `generateManualRetryJobId`, because the server may still have the old key locked as unknown. |

## Acceptance Criteria
1. Two identical submissions (same campaign, customer, call attempt) result in a single row in the `message_jobs` table.
2. The idempotency key is stable across app restarts.
3. `MessageJobIdempotencyTest` verifies deterministic hashes and bounded, unique manual-retry keys.
4. Mocked WAHA tests prove that a duplicate request does not cause a second `POST /api/sendText` for the same key.
5. `SequentialCallCoordinatorTest` verifies that `UNKNOWN` is not retried automatically and that a job without verified consent is refused.

## References
* Proxy contract: `POST /api/message` with `X-Idempotency-Key`; WAHA call: `POST /api/sendText`.
* PRD Section **FR‑07** – Follow‑Up Message job creation and deduplication.
