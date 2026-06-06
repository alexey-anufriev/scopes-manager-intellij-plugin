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
import java.io.Closeable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

const val MCP_PLUGIN_ID = "com.intellij.mcpServer"

private const val MCP_PROJECT_PATH_HEADER = "IJ_MCP_SERVER_PROJECT_PATH"
private const val MCP_SESSION_ID_HEADER = "mcp-session-id"
private val MCP_JSON = Json { ignoreUnknownKeys = true }
private val EMPTY_PARAMS: JsonObject = buildJsonObject {}

class McpHttpClient private constructor(
    private val streamUri: URI,
    private val projectPath: String,
) : Closeable {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    private var nextId = 1
    private var sessionId: String? = null

    fun initialize() {
        request(
            "initialize",
            buildJsonObject {
                put("protocolVersion", "2024-11-05")
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") {
                    put("name", "scopes-manager-integration-test")
                    put("version", "1.0.0")
                }
            },
        )
        notification("notifications/initialized")
    }

    fun listToolNames(): List<String> {
        val response = request("tools/list")
        return response.resultObject()
            .getValue("tools")
            .jsonArray
            .map { it.jsonObject.getValue("name").jsonPrimitive.content }
    }

    fun callTextTool(name: String, arguments: Map<String, String> = emptyMap()): String {
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
        return response
    }

    private fun notification(method: String, params: JsonObject = EMPTY_PARAMS) {
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
            throw AssertionError("MCP HTTP request failed with status ${response.statusCode()}: ${response.body()}")
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

    companion object {
        fun from(port: Int, projectPath: String): McpHttpClient =
            McpHttpClient(URI.create("http://127.0.0.1:$port/stream"), projectPath)
    }
}

private fun JsonObject.resultObject(): JsonObject =
    getValue("result").jsonObject
