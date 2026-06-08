package com.alexey_anufriev.scopes_manager

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

const val MCP_PLUGIN_ID = "com.intellij.mcpServer"

private const val MCP_PROJECT_PATH_HEADER = "IJ_MCP_SERVER_PROJECT_PATH"
private const val MCP_SESSION_ID_HEADER = "mcp-session-id"
private val MCP_JSON = Json { ignoreUnknownKeys = true }
private val EMPTY_PARAMS: JsonObject = buildJsonObject {}

class McpHttpClient private constructor(
    private val baseUri: URI,
    private val projectPath: String,
) : Closeable {
    private val streamUri = baseUri.resolve("/stream")
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    private var nextId = 1
    private var sessionId: String? = null
    private var legacyMessageUri: URI? = null
    private var legacyStream: InputStream? = null
    private var legacyReaderThread: Thread? = null
    private val legacyResponses = LinkedBlockingQueue<JsonObject>()
    private val legacyReadError = AtomicReference<Throwable?>()

    fun initialize() {
        logMcpCheckpoint("Initializing")
        val params = buildJsonObject {
            put("protocolVersion", "2024-11-05")
            putJsonObject("capabilities") {}
            putJsonObject("clientInfo") {
                put("name", "scopes-manager-integration-test")
                put("version", "1.0.0")
            }
        }
        try {
            request("initialize", params)
        } catch (error: McpHttpStatusException) {
            if (error.statusCode != 404) {
                throw error
            }
            logMcpCheckpoint("Switching to legacy SSE transport")
            startLegacySseTransport()
            request("initialize", params)
        }
        notification("notifications/initialized")
        logMcpCheckpoint("Initialized")
    }

    fun listToolNames(): List<String> {
        logMcpCheckpoint("Listing tools")
        val response = request("tools/list")
        return response.resultObject()
            .getValue("tools")
            .jsonArray
            .map { it.jsonObject.getValue("name").jsonPrimitive.content }
    }

    fun callTextTool(name: String, arguments: Map<String, String> = emptyMap()): String {
        logMcpCheckpoint("Calling tool '$name'")
        val response = request(
            "tools/call",
            buildJsonObject {
                put("name", name)
                putJsonObject("arguments") {
                    arguments.forEach { (key, value) -> put(key, value) }
                }
            },
        )
        val result = response.resultObject()
        assertEquals(false, result["isError"]?.jsonPrimitive?.boolean ?: false)
        return result.getValue("content")
            .jsonArray
            .joinToString("\n") { content ->
                content.jsonObject.getValue("text").jsonPrimitive.content
            }
    }

    private fun request(method: String, params: JsonObject = EMPTY_PARAMS): JsonObject {
        val id = nextId++
        logMcpCheckpoint("Request '$method' started")
        val response = send(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                put("params", params)
            },
        )

        val responseId = response["id"]?.jsonPrimitive?.int
        if (responseId != id) {
            throw AssertionError("MCP HTTP request '$method' returned response id '$responseId' instead of '$id': $response")
        }
        response["error"]?.let { throw AssertionError("MCP HTTP request '$method' failed: $it") }
        logMcpCheckpoint("Request '$method' succeeded")
        return response
    }

    private fun notification(method: String, params: JsonObject = EMPTY_PARAMS) {
        logMcpCheckpoint("Notification '$method' started")
        send(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", method)
                put("params", params)
            },
            expectResponseBody = false,
        )
    }

    private fun send(value: JsonObject, expectResponseBody: Boolean = true): JsonObject {
        legacyMessageUri?.let { return sendLegacySse(it, value, expectResponseBody) }

        val request = HttpRequest.newBuilder(streamUri)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .header(MCP_PROJECT_PATH_HEADER, projectPath)
            .apply {
                sessionId?.let { header(MCP_SESSION_ID_HEADER, it) }
            }
            .POST(HttpRequest.BodyPublishers.ofString(MCP_JSON.encodeToString(JsonObject.serializer(), value)))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        sessionId = response.headers().firstValue(MCP_SESSION_ID_HEADER).orElse(sessionId)

        if (response.statusCode() !in 200..299) {
            throw McpHttpStatusException(response.statusCode(), response.body())
        }

        val body = response.body().trim()
        if (body.isEmpty()) {
            if (!expectResponseBody) {
                return EMPTY_PARAMS
            }
            throw AssertionError("MCP HTTP request returned an empty response body")
        }
        if (!expectResponseBody && body == "null") {
            return EMPTY_PARAMS
        }

        return parseHttpResponseBody(body)
    }

    private fun startLegacySseTransport() {
        if (legacyMessageUri != null) {
            return
        }

        logMcpCheckpoint("Opening legacy SSE stream")
        val ready = CountDownLatch(1)
        val request = HttpRequest.newBuilder(baseUri.resolve("/sse"))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "text/event-stream")
            .header(MCP_PROJECT_PATH_HEADER, projectPath)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

        if (response.statusCode() !in 200..299) {
            throw McpHttpStatusException(response.statusCode(), String(response.body().readAllBytes(), StandardCharsets.UTF_8))
        }

        legacyStream = response.body()
        legacyReaderThread = Thread(
            {
                try {
                    readLegacySseStream(response.body(), ready)
                } catch (throwable: Throwable) {
                    legacyReadError.set(throwable)
                    ready.countDown()
                }
            },
            "mcp-legacy-sse-reader",
        ).apply {
            isDaemon = true
            start()
        }

        if (!ready.await(10, TimeUnit.SECONDS)) {
            throw AssertionError("Timed out waiting for MCP legacy SSE endpoint")
        }
        legacyReadError.get()?.let { throw AssertionError("MCP legacy SSE reader failed", it) }
        if (legacyMessageUri == null) {
            throw AssertionError("MCP legacy SSE stream did not provide a message endpoint")
        }
        logMcpCheckpoint("Legacy SSE stream ready")
    }

    private fun readLegacySseStream(input: InputStream, ready: CountDownLatch) {
        val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        var event: String? = null
        val data = StringBuilder()

        while (!Thread.currentThread().isInterrupted) {
            val line = reader.readLine() ?: break
            when {
                line.isEmpty() -> {
                    handleLegacySseEvent(event, data.toString().trim(), ready)
                    event = null
                    data.clear()
                }
                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) {
                        data.append('\n')
                    }
                    data.append(line.removePrefix("data:").trimStart())
                }
            }
        }
    }

    private fun handleLegacySseEvent(event: String?, data: String, ready: CountDownLatch) {
        if (data.isEmpty() || data == "null") {
            return
        }

        if (event == "endpoint") {
            legacyMessageUri = if (data.startsWith("http://") || data.startsWith("https://")) {
                URI.create(data)
            } else {
                baseUri.resolve(data)
            }
            ready.countDown()
            return
        }

        legacyResponses.add(MCP_JSON.parseToJsonElement(data).jsonObject)
    }

    private fun sendLegacySse(messageUri: URI, value: JsonObject, expectResponseBody: Boolean): JsonObject {
        val response = client.send(
            HttpRequest.newBuilder(messageUri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header(MCP_PROJECT_PATH_HEADER, projectPath)
                .POST(HttpRequest.BodyPublishers.ofString(MCP_JSON.encodeToString(JsonObject.serializer(), value)))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        if (response.statusCode() !in 200..299) {
            throw McpHttpStatusException(response.statusCode(), response.body())
        }
        if (!expectResponseBody) {
            return EMPTY_PARAMS
        }

        val expectedId = value["id"]?.jsonPrimitive?.int
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (System.nanoTime() < deadline) {
            legacyReadError.get()?.let { throw AssertionError("MCP legacy SSE reader failed", it) }
            val responseMessage = legacyResponses.poll(250, TimeUnit.MILLISECONDS) ?: continue
            if (responseMessage["id"]?.jsonPrimitive?.int == expectedId) {
                return responseMessage
            }
        }

        throw AssertionError("Timed out waiting for MCP legacy SSE response id '$expectedId'")
    }

    private fun parseHttpResponseBody(body: String): JsonObject {
        if (!body.startsWith("event:") && !body.startsWith("data:") && !body.startsWith(":")) {
            return MCP_JSON.parseToJsonElement(body).jsonObject
        }

        val data = body.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trimStart() }
            .firstOrNull { it != "null" }
            ?.trim()

        if (data.isNullOrEmpty()) {
            throw AssertionError("MCP HTTP SSE response did not contain a data payload: $body")
        }
        return MCP_JSON.parseToJsonElement(data).jsonObject
    }

    override fun close() {
        logMcpCheckpoint("Closing")
        legacyReaderThread?.interrupt()
        legacyStream?.close()
        sessionId?.let { id ->
            val request = HttpRequest.newBuilder(streamUri)
                .timeout(Duration.ofSeconds(10))
                .header(MCP_PROJECT_PATH_HEADER, projectPath)
                .header(MCP_SESSION_ID_HEADER, id)
                .DELETE()
                .build()
            client.send(request, HttpResponse.BodyHandlers.discarding())
        }
    }

    private fun logMcpCheckpoint(message: String) {
        println("[integration-test:mcp] $message")
    }

    companion object {
        fun from(port: Int, projectPath: String): McpHttpClient =
            McpHttpClient(URI.create("http://127.0.0.1:$port"), projectPath)
    }
}

private class McpHttpStatusException(
    val statusCode: Int,
    body: String,
) : AssertionError("MCP HTTP request failed with status $statusCode: $body")

private fun JsonObject.resultObject(): JsonObject =
    getValue("result").jsonObject
