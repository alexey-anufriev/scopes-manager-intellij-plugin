package com.alexey_anufriev.scopes_manager.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

internal const val MCP_PROJECT_PATH_HEADER = "IJ_MCP_SERVER_PROJECT_PATH"
internal val MCP_JSON = Json { ignoreUnknownKeys = true }
internal val EMPTY_MCP_PARAMS: JsonObject = buildJsonObject {}

/** Exchanges JSON-RPC messages with an MCP server. */
internal interface McpTransport : Closeable {
    /** Sends [value] and returns its JSON response when one is expected. */
    fun send(value: JsonObject, expectResponseBody: Boolean = true): JsonObject
}

/** Implements the current MCP streamable HTTP transport. */
internal class StreamableHttpTransport(
    private val baseUri: URI,
    private val projectPath: String,
) : McpTransport {
    private val streamUri = baseUri.resolve("/stream")
    private val client = newMcpHttpClient()
    private var sessionId: String? = null

    override fun send(value: JsonObject, expectResponseBody: Boolean): JsonObject {
        val request = HttpRequest.newBuilder(streamUri)
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .header(MCP_PROJECT_PATH_HEADER, projectPath)
            .apply { sessionId?.let { header(MCP_SESSION_ID_HEADER, it) } }
            .POST(HttpRequest.BodyPublishers.ofString(encodeMcpMessage(value)))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        sessionId = response.headers().firstValue(MCP_SESSION_ID_HEADER).orElse(sessionId)
        requireSuccessfulMcpResponse(response.statusCode(), response.body())

        val body = response.body().trim()
        if (body.isEmpty()) {
            if (!expectResponseBody) {
                return EMPTY_MCP_PARAMS
            }
            throw AssertionError("MCP HTTP request returned an empty response body")
        }
        if (!expectResponseBody && body == "null") {
            return EMPTY_MCP_PARAMS
        }
        return parseResponseBody(body)
    }

    override fun close() {
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

    private fun parseResponseBody(body: String): JsonObject {
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
}

/** Implements the legacy MCP server-sent-events transport. */
internal class LegacySseTransport(
    private val baseUri: URI,
    private val projectPath: String,
) : McpTransport {
    private val client = newMcpHttpClient()
    private val responses = LinkedBlockingQueue<JsonObject>()
    private val readError = AtomicReference<Throwable?>()
    private var messageUri: URI? = null
    private var stream: InputStream? = null
    private var readerThread: Thread? = null

    init {
        openStream()
    }

    override fun send(value: JsonObject, expectResponseBody: Boolean): JsonObject {
        val endpoint = messageUri ?: throw AssertionError("MCP legacy SSE message endpoint is unavailable")
        val response = client.send(
            HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header(MCP_PROJECT_PATH_HEADER, projectPath)
                .POST(HttpRequest.BodyPublishers.ofString(encodeMcpMessage(value)))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        requireSuccessfulMcpResponse(response.statusCode(), response.body())
        if (!expectResponseBody) {
            return EMPTY_MCP_PARAMS
        }

        val expectedId = value["id"]?.jsonPrimitive?.int
        val deadline = System.nanoTime() + REQUEST_TIMEOUT.toNanos()
        while (System.nanoTime() < deadline) {
            readError.get()?.let { throw AssertionError("MCP legacy SSE reader failed", it) }
            val responseMessage = responses.poll(250, TimeUnit.MILLISECONDS) ?: continue
            if (responseMessage["id"]?.jsonPrimitive?.int == expectedId) {
                return responseMessage
            }
        }
        throw AssertionError("Timed out waiting for MCP legacy SSE response id '$expectedId'")
    }

    override fun close() {
        readerThread?.interrupt()
        stream?.close()
    }

    private fun openStream() {
        logMcpTransportCheckpoint("Opening legacy SSE stream")
        val ready = CountDownLatch(1)
        val request = HttpRequest.newBuilder(baseUri.resolve("/sse"))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "text/event-stream")
            .header(MCP_PROJECT_PATH_HEADER, projectPath)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            throw McpHttpStatusException(
                response.statusCode(),
                String(response.body().readAllBytes(), StandardCharsets.UTF_8),
            )
        }

        stream = response.body()
        readerThread = Thread(
            {
                try {
                    readStream(response.body(), ready)
                } catch (error: Throwable) {
                    readError.set(error)
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
        readError.get()?.let { throw AssertionError("MCP legacy SSE reader failed", it) }
        if (messageUri == null) {
            throw AssertionError("MCP legacy SSE stream did not provide a message endpoint")
        }
        logMcpTransportCheckpoint("Legacy SSE stream ready")
    }

    private fun readStream(input: InputStream, ready: CountDownLatch) {
        val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        var event: String? = null
        val data = StringBuilder()

        while (!Thread.currentThread().isInterrupted) {
            val line = reader.readLine() ?: break
            when {
                line.isEmpty() -> {
                    handleEvent(event, data.toString().trim(), ready)
                    event = null
                    data.clear()
                }
                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.removePrefix("data:").trimStart())
                }
            }
        }
    }

    private fun handleEvent(event: String?, data: String, ready: CountDownLatch) {
        if (data.isEmpty() || data == "null") return
        if (event == "endpoint") {
            messageUri = if (data.startsWith("http://") || data.startsWith("https://")) {
                URI.create(data)
            } else {
                baseUri.resolve(data)
            }
            ready.countDown()
        } else {
            responses.add(MCP_JSON.parseToJsonElement(data).jsonObject)
        }
    }
}

/** Reports a non-successful response returned by an MCP HTTP endpoint. */
internal class McpHttpStatusException(
    val statusCode: Int,
    body: String,
) : AssertionError("MCP HTTP request failed with status $statusCode: $body")

private const val MCP_SESSION_ID_HEADER = "mcp-session-id"
private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)

private fun newMcpHttpClient(): HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build()

private fun encodeMcpMessage(value: JsonObject): String =
    MCP_JSON.encodeToString(JsonObject.serializer(), value)

private fun requireSuccessfulMcpResponse(statusCode: Int, body: String) {
    if (statusCode !in 200..299) {
        throw McpHttpStatusException(statusCode, body)
    }
}

private fun logMcpTransportCheckpoint(message: String) {
    println("[integration-test:mcp-transport] $message")
}
