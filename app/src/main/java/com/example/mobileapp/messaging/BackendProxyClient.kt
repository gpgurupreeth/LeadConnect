package com.example.mobileapp.messaging

import com.example.mobileapp.domain.model.MessageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

sealed class ProxyContractStatus {
    data class Verified(
        val liveDispatchReady: Boolean = false,
        val sessionConnected: Boolean = false,
        val note: String = ""
    ) : ProxyContractStatus()
    data class Unverified(val reason: String, val actionableGuide: String) : ProxyContractStatus()
    data class Offline(val url: String, val message: String) : ProxyContractStatus()
}

/**
 * Backend Proxy Client implementing [IMessagingProvider].
 *
 * Implements the architecture documented in docs/backend-proxy.md:
 * - Client authenticates via short-lived JWT token over HTTPS/HTTP.
 * - Injects X-Idempotency-Key header to prevent duplicate dispatches.
 * - The WAHA API key, dashboard password, and session credentials are strictly maintained
 *   on the backend proxy and are NEVER shipped in this APK.
 * - If the proxy, WAHA gateway, or WhatsApp session is unconfigured or unreachable,
 *   safely returns informative, actionable errors.
 */
class BackendProxyClient(
    var baseUrl: String = DEFAULT_BASE_URL,
    var sessionToken: String? = null
) : IMessagingProvider {

    /**
     * Verifies the backend proxy contract via GET /api/health probe.
     */
    suspend fun verifyContract(): ProxyContractStatus = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://"))) {
            return@withContext ProxyContractStatus.Unverified(
                reason = "Invalid Proxy URL format",
                actionableGuide = "Configure a valid HTTP/HTTPS URL (e.g. http://10.0.2.2:8081 for the emulator, " +
                    "or http://<PC-LAN-IP>:8081 for a physical phone) in Settings."
            )
        }

        if (sessionToken.isNullOrBlank()) {
            return@withContext ProxyContractStatus.Unverified(
                reason = "Missing JWT Session Token",
                actionableGuide = "Enter a valid JWT session token in Settings to authenticate requests."
            )
        }

        try {
            val url = URL("$cleanUrl/api/health")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = HEALTH_READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                if (!sessionToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $sessionToken")
                }
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                ""
            }
            connection.disconnect()

            if (responseCode in 200..299) {
                val health = parseWhatsAppHealth(responseBody)
                ProxyContractStatus.Verified(
                    liveDispatchReady = health.liveReady,
                    sessionConnected = health.sessionConnected,
                    note = health.note
                )
            } else if (responseCode == 401) {
                ProxyContractStatus.Unverified(
                    reason = "HTTP 401 Unauthorized",
                    actionableGuide = "JWT token rejected by proxy. Acquire a fresh session token."
                )
            } else if (responseCode == 404) {
                ProxyContractStatus.Unverified(
                    reason = "HTTP 404 Not Found at /api/health",
                    actionableGuide = "The URL is reachable, but it is not the backend proxy. " +
                        "Check the port: this project runs on 8081 by default. " +
                        "Port 8080 is often occupied by Apache/XAMPP."
                )
            } else {
                ProxyContractStatus.Offline(
                    url = cleanUrl,
                    message = "Proxy returned unexpected HTTP $responseCode on /api/health probe."
                )
            }
        } catch (e: ConnectException) {
            ProxyContractStatus.Offline(
                url = cleanUrl,
                message = "Connection refused. Start the backend proxy with backend-proxy\\run-proxy.ps1 " +
                    "(default port 8081) and confirm the phone can reach this PC."
            )
        } catch (e: SocketTimeoutException) {
            ProxyContractStatus.Offline(
                url = cleanUrl,
                message = "Connection timed out. Confirm the phone is on the same Wi-Fi and that " +
                    "inbound TCP port 8081 is allowed through Windows Firewall."
            )
        } catch (e: UnknownHostException) {
            ProxyContractStatus.Unverified(
                reason = "Unknown host: ${e.message}",
                actionableGuide = "Use the PC's current LAN IPv4 address (run ipconfig on the PC), " +
                    "not a hostname."
            )
        } catch (e: Exception) {
            ProxyContractStatus.Offline(
                url = cleanUrl,
                message = "Gateway offline (${e.javaClass.simpleName}: ${e.message}). " +
                    "Start the proxy with backend-proxy\\run-proxy.ps1 on port 8081."
            )
        }
    }

    private data class WhatsAppHealth(
        val liveReady: Boolean,
        val sessionConnected: Boolean,
        val note: String
    )

    /**
     * Parses the /api/health payload to determine whether real WhatsApp dispatch is enabled.
     * New servers expose `waha` and a top-level `live_send_enabled`; `waakg` is retained as
     * a deprecated compatibility fallback. Missing or unreadable data returns false so the
     * UI never claims live sending is ready.
     */
    private fun parseWhatsAppHealth(healthJson: String): WhatsAppHealth {
        return try {
            val root = JSONObject(healthJson)
            val statusObject = root.optJSONObject("waha") ?: root.optJSONObject("waakg")
                ?: return WhatsAppHealth(
                    false,
                    false,
                    "Proxy reachable, but the server did not report WhatsApp gateway status."
                )

            val configured = statusObject.optBoolean("configured", false)
            // Older servers only have live_enabled. New servers expose
            // session_live_enabled (WAHA session) and top-level live_send_enabled
            // (session ready AND server kill switch open).
            val sessionLive = statusObject.optBoolean(
                "session_live_enabled",
                statusObject.optBoolean("live_enabled", false)
            )
            val serverGateOpen = if (root.has("live_send_enabled")) {
                root.optBoolean("live_send_enabled", false)
            } else {
                statusObject.optBoolean("live_enabled", false)
            }
            val liveReady = configured && sessionLive && serverGateOpen
            val status = statusObject.optString("status", "")
            val serverMessage = statusObject.optString("message", "").trim()
            val blockedReason = statusObject.optString("live_send_blocked_reason", "").trim()

            val note = when {
                liveReady ->
                    "Proxy reachable; WAHA WhatsApp session is connected and live dispatch is enabled."

                blockedReason == "kill_switch_disabled" ->
                    "Proxy reachable; WAHA session is connected, but live sending is disabled by " +
                        "the server kill switch (WAHA_ENABLE_LIVE=false). No messages will be sent."

                blockedReason == "provider_disabled" ->
                    "Proxy reachable, but WhatsApp dispatch is disabled on the server " +
                        "(WHATSAPP_PROVIDER is not waha). No messages will be sent."

                blockedReason == "waha_not_configured" ->
                    "Proxy reachable, but WAHA credentials are incomplete. " +
                        "Set WAHA_BASE_URL and WAHA_API_KEY on the server."

                status == "connected" && configured ->
                    "Proxy reachable; WAHA session is connected, but live dispatch is gated on the server."

                status == "scan_qr" || status == "pairing_required" ->
                    "Proxy reachable; WhatsApp session needs pairing. Open the WAHA dashboard " +
                        "on the server and scan the QR code with the dedicated test phone."

                status == "unauthorized" ->
                    "Proxy reachable, but WAHA rejected the backend API key. " +
                        "Check WAHA_API_KEY on the server."

                status == "session_not_found" ->
                    "Proxy reachable, but the configured WAHA session does not exist. " +
                        "Create and pair it in the WAHA dashboard."

                status == "session_not_connected" ->
                    serverMessage.ifBlank {
                        "Proxy reachable, but the WhatsApp session is not connected. " +
                            "Start or pair it in the WAHA dashboard."
                    }

                status == "stopped" ->
                    serverMessage.ifBlank {
                        "Proxy reachable, but the WhatsApp session is stopped. " +
                            "Start it in the WAHA dashboard."
                    }

                status == "starting" ->
                    "Proxy reachable; the WhatsApp session is starting. Try again shortly."

                status == "failed" ->
                    serverMessage.ifBlank {
                        "Proxy reachable, but the WhatsApp session failed. " +
                            "Check the WAHA logs and restart it."
                    }

                status == "unreachable" ->
                    "Proxy reachable, but WAHA is unreachable at the configured server endpoint. " +
                        "Check the WAHA container and its local port."

                configured ->
                    "Proxy reachable, but WhatsApp dispatch is gated: the WAHA session is not " +
                        "connected or live dispatch is disabled on the server."

                else ->
                    "Proxy reachable, but WhatsApp dispatch is gated: disabled on the server. " +
                        "Set WHATSAPP_PROVIDER=waha, WAHA_ENABLE_LIVE=true, and WAHA_API_KEY " +
                        "on the backend to enable sending."
            }

            WhatsAppHealth(liveReady, sessionLive, note)
        } catch (_: Exception) {
            WhatsAppHealth(
                false,
                false,
                "Proxy reachable, but its WhatsApp status response could not be parsed."
            )
        }
    }

    override suspend fun sendMessage(payload: MessagePayload): MessageSendResult = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")

        // Guard 1: Validate payload schema & idempotency key
        if (payload.jobId.isBlank()) {
            return@withContext MessageSendResult(
                success = false,
                error = "Payload validation failure: Missing idempotency jobId.",
                outcome = DeliveryOutcome.NOT_SENT
            )
        }

        if (payload.phoneNumber.isBlank()) {
            return@withContext MessageSendResult(
                success = false,
                error = "Payload validation failure: Missing recipient phone number.",
                outcome = DeliveryOutcome.NOT_SENT
            )
        }

        if (!payload.consent) {
            return@withContext MessageSendResult(
                success = false,
                error = "Payload validation failure: WhatsApp consent is not recorded.",
                outcome = DeliveryOutcome.NOT_SENT
            )
        }

        if (payload.optOut) {
            return@withContext MessageSendResult(
                success = false,
                error = "Payload validation failure: Recipient has opted out of WhatsApp.",
                outcome = DeliveryOutcome.NOT_SENT
            )
        }

        // Guard 2: Verify proxy configuration
        if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://"))) {
            return@withContext MessageSendResult(
                success = false,
                error = "WhatsApp dispatch gated: Missing or invalid backend proxy URL.",
                outcome = DeliveryOutcome.NOT_SENT
            )
        }

        if (sessionToken.isNullOrBlank()) {
            return@withContext MessageSendResult(
                success = false,
                error = "WhatsApp dispatch gated: Missing short-lived JWT session token.",
                outcome = DeliveryOutcome.NOT_SENT
            )
        }

        // Guard 3: Execute authenticated HTTP POST /api/message
        try {
            val url = URL("$cleanUrl/api/message")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = MESSAGE_READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer ${sessionToken?.trim()}")
                setRequestProperty("X-Idempotency-Key", payload.jobId)
            }

            // Construct payload matching backend proxy contract
            val jsonBody = JSONObject().apply {
                put("sessionId", "default")
                put("jid", "${payload.phoneNumber.replace("+", "")}@s.whatsapp.net")
                put("content", "Automated follow-up message for template: ${payload.templateName}")
                put("jobId", payload.jobId)
                put("templateName", payload.templateName)
                put("consent", payload.consent)
                put("optOut", payload.optOut)
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val statusCode = connection.responseCode
            val deliveryHeader = connection.getHeaderField("X-Delivery-Outcome")
                ?.trim()
                ?.lowercase()
            val isSuccess = statusCode in 200..299

            val inputStream = if (isSuccess) connection.inputStream else connection.errorStream
            val responseText = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            connection.disconnect()

            if (isSuccess) {
                val responseJson = try { JSONObject(responseText) } catch (e: Exception) { null }
                val messageId = if (responseJson != null && !responseJson.isNull("messageId")) {
                    responseJson.optString("messageId").takeIf { it.isNotBlank() }
                } else {
                    null
                }
                MessageSendResult(
                    success = true,
                    providerMessageId = messageId,
                    outcome = DeliveryOutcome.ACCEPTED
                )
            } else {
                val errorMsg = when (statusCode) {
                    401 -> "Proxy 401 Unauthorized: Expired or invalid JWT token."
                    403 -> "Proxy 403 Forbidden: $responseText"
                    404 -> "Proxy 404 Not Found: this URL is not the backend proxy. Check the port (8081) and base URL."
                    400 -> "Proxy 400 Bad Request: $responseText"
                    409 -> "Proxy 409 Conflict: this jobId is already in-flight or completed."
                    429 -> "Proxy 429 Rate Limited: Backend proxy rate limit exceeded."
                    503 -> "Proxy 503: WhatsApp dispatch is disabled or the WAHA session is unavailable. " +
                        "No message was sent. $responseText"
                    502 -> "Proxy 502: WhatsApp gateway rejected or could not accept the request. " +
                        "Check the WAHA container and the backend WHATSAPP_PROVIDER/WAHA_* settings. " +
                        responseText
                    504 -> "Proxy 504: WhatsApp gateway timed out. Delivery outcome may be unknown. " +
                        "Do not retry automatically; verify in WhatsApp first."
                    500 -> "Proxy 500: unexpected server error. Delivery outcome is unknown. " +
                        "Do not retry automatically; verify in WhatsApp first."
                    else -> "Proxy Error ($statusCode): $responseText"
                }
                MessageSendResult(
                    success = false,
                    error = errorMsg,
                    outcome = classifyHttpOutcome(statusCode, deliveryHeader)
                )
            }
        } catch (e: ConnectException) {
            MessageSendResult(
                success = false,
                error = "Proxy Gateway Offline: connection refused at $cleanUrl. " +
                    "Start backend-proxy\\run-proxy.ps1 on port 8081.",
                outcome = DeliveryOutcome.NOT_SENT
            )
        } catch (e: UnknownHostException) {
            MessageSendResult(
                success = false,
                error = "Proxy Gateway Offline: unknown host $cleanUrl. " +
                    "Check the backend URL in Settings.",
                outcome = DeliveryOutcome.NOT_SENT
            )
        } catch (e: SocketTimeoutException) {
            MessageSendResult(
                success = false,
                error = "Proxy Gateway Timeout: no response from $cleanUrl. " +
                    "Check Wi-Fi/firewall and that port 8081 is reachable from the phone. " +
                    "Delivery outcome may be unknown; verify in WhatsApp before retrying.",
                outcome = DeliveryOutcome.UNKNOWN
            )
        } catch (e: Exception) {
            MessageSendResult(
                success = false,
                error = "Proxy Gateway error (${e.javaClass.simpleName}) at $cleanUrl. " +
                    "Delivery outcome may be unknown; verify in WhatsApp before retrying.",
                outcome = DeliveryOutcome.UNKNOWN
            )
        }
    }

    private fun classifyHttpOutcome(
        statusCode: Int,
        deliveryHeader: String?
    ): DeliveryOutcome {
        return when (deliveryHeader) {
            "accepted" -> DeliveryOutcome.ACCEPTED
            "unknown" -> DeliveryOutcome.UNKNOWN
            "not_sent" -> DeliveryOutcome.NOT_SENT
            else -> when (statusCode) {
                408, 500, 504 -> DeliveryOutcome.UNKNOWN
                else -> DeliveryOutcome.NOT_SENT
            }
        }
    }

    override suspend fun getDeliveryStatus(jobId: String): MessageStatus {
        return MessageStatus.PENDING
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8081"

        private const val CONNECT_TIMEOUT_MS = 5_000

        /** Must exceed the proxy's WAHA health probe timeout (3s per probe). */
        private const val HEALTH_READ_TIMEOUT_MS = 10_000

        /** Must exceed the proxy's upstream WAHA timeout (10s default in .env). */
        private const val MESSAGE_READ_TIMEOUT_MS = 15_000
    }
}
