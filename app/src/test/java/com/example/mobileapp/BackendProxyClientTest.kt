package com.example.mobileapp

import com.example.mobileapp.messaging.BackendProxyClient
import com.example.mobileapp.messaging.DeliveryOutcome
import com.example.mobileapp.messaging.MessagePayload
import com.example.mobileapp.messaging.ProxyContractStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

class BackendProxyClientTest {

    @Test
    fun `test payload validation rejects blank jobId to prevent non-idempotent dispatches`() = runBlocking {
        val client = BackendProxyClient(baseUrl = "http://10.0.2.2:8081", sessionToken = "jwt_sample")
        val payload = MessagePayload(
            jobId = "",
            phoneNumber = "+919876543210",
            templateName = "welcome_followup",
            parameters = emptyMap()
        )

        val result = client.sendMessage(payload)
        assertFalse(result.success)
        assertTrue(result.error!!.contains("Missing idempotency jobId"))
    }

    @Test
    fun `test payload validation rejects empty phone number`() = runBlocking {
        val client = BackendProxyClient(baseUrl = "http://10.0.2.2:8081", sessionToken = "jwt_sample")
        val payload = MessagePayload(
            jobId = "deterministic_sha256_id",
            phoneNumber = "",
            templateName = "welcome_followup",
            parameters = emptyMap()
        )

        val result = client.sendMessage(payload)
        assertFalse(result.success)
        assertTrue(result.error!!.contains("Missing recipient phone number"))
    }

    @Test
    fun `test unconfigured proxy URL returns safe gated error without throwing`() = runBlocking {
        val client = BackendProxyClient(baseUrl = "", sessionToken = "jwt_sample")
        val payload = MessagePayload(
            jobId = "deterministic_sha256_id",
            phoneNumber = "+919876543210",
            templateName = "welcome_followup",
            parameters = emptyMap(),
            consent = true
        )

        val result = client.sendMessage(payload)
        assertFalse(result.success)
        assertTrue(result.error!!.contains("Missing or invalid backend proxy URL"))
    }

    @Test
    fun `test missing JWT session token returns authentication error`() = runBlocking {
        val client = BackendProxyClient(baseUrl = "http://10.0.2.2:8081", sessionToken = null)
        val payload = MessagePayload(
            jobId = "deterministic_sha256_id",
            phoneNumber = "+919876543210",
            templateName = "welcome_followup",
            parameters = emptyMap(),
            consent = true
        )

        val result = client.sendMessage(payload)
        assertFalse(result.success)
        assertTrue(result.error!!.contains("Missing short-lived JWT session token"))
    }

    @Test
    fun `test missing consent is rejected locally without a network request`() = runBlocking {
        val client = BackendProxyClient(baseUrl = "http://10.0.2.2:8081", sessionToken = "jwt_sample")
        val payload = MessagePayload(
            jobId = "job-no-consent",
            phoneNumber = "+919876543210",
            templateName = "welcome_followup",
            parameters = emptyMap(),
            consent = false
        )

        val result = client.sendMessage(payload)
        assertFalse(result.success)
        assertEquals(DeliveryOutcome.NOT_SENT, result.outcome)
        assertTrue(result.error!!.contains("consent", ignoreCase = true))
    }

    @Test
    fun `test opted out recipient is rejected locally without a network request`() = runBlocking {
        val client = BackendProxyClient(baseUrl = "http://10.0.2.2:8081", sessionToken = "jwt_sample")
        val payload = MessagePayload(
            jobId = "job-opt-out",
            phoneNumber = "+919876543210",
            templateName = "welcome_followup",
            parameters = emptyMap(),
            consent = true,
            optOut = true
        )

        val result = client.sendMessage(payload)
        assertFalse(result.success)
        assertEquals(DeliveryOutcome.NOT_SENT, result.outcome)
        assertTrue(result.error!!.contains("opted out", ignoreCase = true))
    }

    @Test
    fun `test contract verification catches invalid URL format`() = runBlocking {
        val client = BackendProxyClient(baseUrl = "not_a_valid_url", sessionToken = "jwt_token")
        val status = client.verifyContract()

        assertTrue(status is ProxyContractStatus.Unverified)
        val unverified = status as ProxyContractStatus.Unverified
        assertEquals("Invalid Proxy URL format", unverified.reason)
    }

    @Test
    fun `test contract verification catches missing token`() = runBlocking {
        val client = BackendProxyClient(baseUrl = "http://localhost:8081", sessionToken = "")
        val status = client.verifyContract()

        assertTrue(status is ProxyContractStatus.Unverified)
        val unverified = status as ProxyContractStatus.Unverified
        assertEquals("Missing JWT Session Token", unverified.reason)
    }

    @Test
    fun `test unreachable proxy reports gateway offline with actionable instructions`() = runBlocking {
        // Port 59999 should not have a running proxy in tests
        val client = BackendProxyClient(baseUrl = "http://127.0.0.1:59999", sessionToken = "jwt_token")
        val status = client.verifyContract()

        assertTrue(status is ProxyContractStatus.Offline)
        val offline = status as ProxyContractStatus.Offline
        assertTrue(offline.message.contains("run-proxy.ps1"))
    }

    @Test
    fun `test http 404 from wrong server is unverified rather than offline`() = runBlocking {
        val (serverSocket, responder) = startRawHttpServer("HTTP/1.1 404 Not Found", "")
        try {
            val client = BackendProxyClient(
                baseUrl = "http://127.0.0.1:${serverSocket.localPort}",
                sessionToken = "jwt_token"
            )
            val status = client.verifyContract()

            assertTrue(status is ProxyContractStatus.Unverified)
            val unverified = status as ProxyContractStatus.Unverified
            assertTrue(unverified.reason.contains("404"))
            assertTrue(unverified.actionableGuide.contains("8081"))
        } finally {
            serverSocket.close()
            responder.join(1000)
        }
    }

    @Test
    fun `test health 200 with disabled waakg verifies proxy but marks dispatch gated`() = runBlocking {
        val health = """{"status":"UP","proxy":"running","auth":{"authenticated":true},"waakg":{"configured":false,"live_enabled":false}}"""
        val (serverSocket, responder) = startRawHttpServer("HTTP/1.1 200 OK", health)
        try {
            val client = BackendProxyClient(
                baseUrl = "http://127.0.0.1:${serverSocket.localPort}",
                sessionToken = "jwt_token"
            )
            val status = client.verifyContract()

            assertTrue(status is ProxyContractStatus.Verified)
            val verified = status as ProxyContractStatus.Verified
            assertFalse(verified.liveDispatchReady)
            assertTrue(verified.note.contains("gated", ignoreCase = true))
        } finally {
            serverSocket.close()
            responder.join(1000)
        }
    }

    @Test
    fun `test health 200 with live waakg reports live dispatch ready`() = runBlocking {
        val health = """{"status":"UP","proxy":"running","auth":{"authenticated":true},"waakg":{"configured":true,"live_enabled":true}}"""
        val (serverSocket, responder) = startRawHttpServer("HTTP/1.1 200 OK", health)
        try {
            val client = BackendProxyClient(
                baseUrl = "http://127.0.0.1:${serverSocket.localPort}",
                sessionToken = "jwt_token"
            )
            val status = client.verifyContract()

            assertTrue(status is ProxyContractStatus.Verified)
            val verified = status as ProxyContractStatus.Verified
            assertTrue(verified.liveDispatchReady)
            assertTrue(verified.note.contains("enabled", ignoreCase = true))
        } finally {
            serverSocket.close()
            responder.join(1000)
        }
    }

    @Test
    fun `test health 200 with live waha reports live dispatch ready`() = runBlocking {
        val health = """{"status":"UP","proxy":"running","auth":{"authenticated":true},"waha":{"configured":true,"live_enabled":true,"status":"connected","message":"WAHA is reachable and the WhatsApp session is connected."}}"""
        val (serverSocket, responder) = startRawHttpServer("HTTP/1.1 200 OK", health)
        try {
            val client = BackendProxyClient(
                baseUrl = "http://127.0.0.1:${serverSocket.localPort}",
                sessionToken = "jwt_token"
            )
            val status = client.verifyContract()

            assertTrue(status is ProxyContractStatus.Verified)
            val verified = status as ProxyContractStatus.Verified
            assertTrue(verified.liveDispatchReady)
            assertTrue(verified.sessionConnected)
            assertTrue(verified.note.contains("WAHA", ignoreCase = true))
            assertTrue(verified.note.contains("enabled", ignoreCase = true))
        } finally {
            serverSocket.close()
            responder.join(1000)
        }
    }

    @Test
    fun `test health kill switch closed reports connected session but blocks dispatch`() = runBlocking {
        val health = """{"status":"UP","proxy":"running","auth":{"authenticated":true},"live_send_enabled":false,"waha":{"configured":true,"session_live_enabled":true,"live_enabled":false,"status":"connected","live_send_blocked_reason":"kill_switch_disabled"}}"""
        val (serverSocket, responder) = startRawHttpServer("HTTP/1.1 200 OK", health)
        try {
            val client = BackendProxyClient(
                baseUrl = "http://127.0.0.1:${serverSocket.localPort}",
                sessionToken = "jwt_token"
            )
            val status = client.verifyContract()

            assertTrue(status is ProxyContractStatus.Verified)
            val verified = status as ProxyContractStatus.Verified
            assertFalse(verified.liveDispatchReady)
            assertTrue(verified.sessionConnected)
            assertTrue(verified.note.contains("kill switch", ignoreCase = true))
        } finally {
            serverSocket.close()
            responder.join(1000)
        }
    }

    @Test
    fun `test health 200 with waha scan qr reports pairing guidance`() = runBlocking {
        val health = """{"status":"UP","proxy":"running","auth":{"authenticated":true},"waha":{"configured":true,"live_enabled":false,"status":"scan_qr","message":"WAHA is reachable, but the WhatsApp session needs pairing."}}"""
        val (serverSocket, responder) = startRawHttpServer("HTTP/1.1 200 OK", health)
        try {
            val client = BackendProxyClient(
                baseUrl = "http://127.0.0.1:${serverSocket.localPort}",
                sessionToken = "jwt_token"
            )
            val status = client.verifyContract()

            assertTrue(status is ProxyContractStatus.Verified)
            val verified = status as ProxyContractStatus.Verified
            assertFalse(verified.liveDispatchReady)
            assertTrue(verified.note.contains("pair", ignoreCase = true))
            assertTrue(verified.note.contains("QR", ignoreCase = true))
        } finally {
            serverSocket.close()
            responder.join(1000)
        }
    }

    @Test
    fun `test health 200 with waha unauthorized reports api key guidance`() = runBlocking {
        val health = """{"status":"UP","proxy":"running","auth":{"authenticated":true},"waha":{"configured":true,"live_enabled":false,"status":"unauthorized","message":"WAHA rejected the backend API key."}}"""
        val (serverSocket, responder) = startRawHttpServer("HTTP/1.1 200 OK", health)
        try {
            val client = BackendProxyClient(
                baseUrl = "http://127.0.0.1:${serverSocket.localPort}",
                sessionToken = "jwt_token"
            )
            val status = client.verifyContract()

            assertTrue(status is ProxyContractStatus.Verified)
            val verified = status as ProxyContractStatus.Verified
            assertFalse(verified.liveDispatchReady)
            assertTrue(verified.note.contains("WAHA_API_KEY", ignoreCase = true))
        } finally {
            serverSocket.close()
            responder.join(1000)
        }
    }

    @Test
    fun `test health prefers new waha status over legacy waakg alias`() = runBlocking {
        val health = """{"status":"UP","proxy":"running","auth":{"authenticated":true},"waha":{"configured":true,"live_enabled":true,"status":"connected"},"waakg":{"configured":false,"live_enabled":false,"status":"unconfigured"}}"""
        val (serverSocket, responder) = startRawHttpServer("HTTP/1.1 200 OK", health)
        try {
            val client = BackendProxyClient(
                baseUrl = "http://127.0.0.1:${serverSocket.localPort}",
                sessionToken = "jwt_token"
            )
            val status = client.verifyContract()

            assertTrue(status is ProxyContractStatus.Verified)
            val verified = status as ProxyContractStatus.Verified
            assertTrue(verified.liveDispatchReady)
        } finally {
            serverSocket.close()
            responder.join(1000)
        }
    }

    @Test
    fun `test send message constructs authenticated idempotent proxy request`() = runBlocking {
        val response = """{"success":true,"messageId":"waha-msg-1","status":"sent"}"""
        val server = CapturingHttpServer("HTTP/1.1 200 OK", response)
        try {
            val client = BackendProxyClient(baseUrl = server.baseUrl, sessionToken = "jwt_token")
            val payload = MessagePayload(
                jobId = "job-42",
                phoneNumber = "+919876543210",
                templateName = "follow_up",
                parameters = emptyMap(),
                consent = true
            )

            val result = client.sendMessage(payload)

            assertTrue(result.success)
            assertEquals("waha-msg-1", result.providerMessageId)
            assertEquals(DeliveryOutcome.ACCEPTED, result.outcome)

            val captured = server.captured.get()
            assertTrue(captured != null)
            assertEquals("POST /api/message HTTP/1.1", captured!!.requestLine)
            assertEquals("Bearer jwt_token", captured.headers["authorization"])
            assertEquals("job-42", captured.headers["x-idempotency-key"])
            assertEquals("application/json; charset=UTF-8", captured.headers["content-type"])

            val json = JSONObject(captured.body)
            assertEquals("default", json.getString("sessionId"))
            assertEquals("919876543210@s.whatsapp.net", json.getString("jid"))
            assertEquals("job-42", json.getString("jobId"))
            assertEquals("follow_up", json.getString("templateName"))
            assertTrue(json.getBoolean("consent"))
            assertFalse(json.getBoolean("optOut"))
            assertTrue(json.getString("content").contains("follow_up"))
        } finally {
            server.close()
        }
    }

    @Test
    fun `test send message does not invent provider id when proxy returns null`() = runBlocking {
        val response = """{"success":true,"messageId":null,"status":"sent"}"""
        val server = CapturingHttpServer("HTTP/1.1 200 OK", response)
        try {
            val client = BackendProxyClient(baseUrl = server.baseUrl, sessionToken = "jwt_token")
            val payload = MessagePayload(
                jobId = "job-no-provider-id",
                phoneNumber = "+919876543210",
                templateName = "follow_up",
                parameters = emptyMap(),
                consent = true
            )

            val result = client.sendMessage(payload)

            assertTrue(result.success)
            assertNull(result.providerMessageId)
        } finally {
            server.close()
        }
    }

    @Test
    fun `test proxy 504 timeout warns against automatic retry`() = runBlocking {
        val (serverSocket, responder) = startRawHttpServer(
            "HTTP/1.1 504 Gateway Timeout",
            """{"detail":"WAHA did not respond in time. Do not retry automatically; verify in WhatsApp first."}"""
        )
        try {
            val client = BackendProxyClient(
                baseUrl = "http://127.0.0.1:${serverSocket.localPort}",
                sessionToken = "jwt_token"
            )
            val payload = MessagePayload(
                jobId = "job-timeout",
                phoneNumber = "+919876543210",
                templateName = "follow_up",
                parameters = emptyMap(),
                consent = true
            )

            val result = client.sendMessage(payload)

            assertFalse(result.success)
            assertTrue(result.error!!.contains("504"))
            assertTrue(result.error!!.contains("Do not retry", ignoreCase = true))
            assertEquals(DeliveryOutcome.UNKNOWN, result.outcome)
        } finally {
            serverSocket.close()
            responder.join(1000)
        }
    }

    @Test
    fun `test default proxy URL uses the documented port 8081`() {
        assertEquals("http://10.0.2.2:8081", BackendProxyClient().baseUrl)
    }

    /**
     * Minimal raw-socket HTTP responder. Android unit tests compile against android.jar,
     * which does not expose com.sun.net.httpserver.
     */
    private fun startRawHttpServer(statusLine: String, body: String): Pair<ServerSocket, Thread> {
        val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val responder = Thread {
            try {
                serverSocket.accept().use { socket ->
                    socket.soTimeout = 3000
                    val reader = socket.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val bytes = body.toByteArray()
                    val response = "$statusLine\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: ${bytes.size}\r\n" +
                        "Connection: close\r\n\r\n" +
                        body
                    socket.getOutputStream().write(response.toByteArray())
                    socket.getOutputStream().flush()
                }
            } catch (_: Exception) {
                // Test teardown closes the socket; ignore.
            }
        }
        responder.isDaemon = true
        responder.start()
        return serverSocket to responder
    }

    private data class CapturedRequest(
        val requestLine: String,
        val headers: Map<String, String>,
        val body: String
    )

    /**
     * Raw-socket HTTP server that captures the first request before responding.
     * Kept dependency-free because Android unit tests compile against android.jar.
     */
    private class CapturingHttpServer(statusLine: String, private val body: String) {
        private val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val baseUrl: String = "http://127.0.0.1:${serverSocket.localPort}"
        val captured = AtomicReference<CapturedRequest?>(null)

        private val responder = Thread {
            try {
                serverSocket.accept().use { socket ->
                    socket.soTimeout = 3000
                    val input = socket.getInputStream()

                    val headerBytes = ByteArrayOutputStream()
                    var terminatorState = 0
                    while (terminatorState < 4) {
                        val value = input.read()
                        if (value < 0) break
                        headerBytes.write(value)
                        terminatorState = when {
                            value == '\r'.code && (terminatorState == 0 || terminatorState == 2) ->
                                terminatorState + 1
                            value == '\n'.code && (terminatorState == 1 || terminatorState == 3) ->
                                terminatorState + 1
                            else -> 0
                        }
                    }

                    val headerText = String(headerBytes.toByteArray(), Charsets.US_ASCII)
                    val headerLines = headerText.split("\r\n").filter { it.isNotEmpty() }
                    val requestLine = headerLines.firstOrNull() ?: ""
                    val headers = headerLines.drop(1).mapNotNull { line ->
                        val separator = line.indexOf(':')
                        if (separator <= 0) null
                        else line.substring(0, separator).trim().lowercase() to
                            line.substring(separator + 1).trim()
                    }.toMap()

                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    val requestBody = ByteArray(contentLength)
                    var offset = 0
                    while (offset < contentLength) {
                        val read = input.read(requestBody, offset, contentLength - offset)
                        if (read < 0) break
                        offset += read
                    }

                    captured.set(
                        CapturedRequest(
                            requestLine = requestLine,
                            headers = headers,
                            body = String(requestBody, 0, offset, Charsets.UTF_8)
                        )
                    )

                    val responseBody = body.toByteArray(Charsets.UTF_8)
                    val response = "$statusLine\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: ${responseBody.size}\r\n" +
                        "Connection: close\r\n\r\n" +
                        body
                    socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
                }
            } catch (_: Exception) {
                // Test teardown closes the socket; ignore.
            }
        }

        init {
            responder.isDaemon = true
            responder.start()
        }

        fun close() {
            try {
                serverSocket.close()
            } catch (_: Exception) {
                // Already closed.
            }
            responder.join(1000)
        }
    }
}
