package com.alexey_anufriev.scopes_manager

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.Closeable
import java.net.URI

internal class McpHttpClient private constructor(
    private val baseUri: URI,
    private val projectPath: String,
) : Closeable {
    private var nextId = 1
    private var transport: McpTransport = StreamableHttpTransport(baseUri, projectPath)

    fun initialize() {
        log("Initializing")
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
            if (error.statusCode != 404) throw error
            log("Switching to legacy SSE transport")
            transport.close()
            transport = LegacySseTransport(baseUri, projectPath)
            request("initialize", params)
        }
        notification("notifications/initialized")
        log("Initialized")
    }

    fun listToolNames(): List<String> {
        log("Listing tools")
        return request("tools/list")
            .resultObject()
            .getValue("tools")
            .jsonArray
            .map { it.jsonObject.getValue("name").jsonPrimitive.content }
    }

    fun callTextTool(name: String, arguments: Map<String, String> = emptyMap()): String {
        log("Calling tool '$name'")
        val result = request(
            "tools/call",
            buildJsonObject {
                put("name", name)
                putJsonObject("arguments") {
                    arguments.forEach { (key, value) -> put(key, value) }
                }
            },
        ).resultObject()

        if (result["isError"]?.jsonPrimitive?.boolean == true) {
            throw AssertionError("MCP tool '$name' returned an error: $result")
        }
        return result.getValue("content")
            .jsonArray
            .joinToString("\n") { it.jsonObject.getValue("text").jsonPrimitive.content }
    }

    private fun request(method: String, params: JsonObject = EMPTY_MCP_PARAMS): JsonObject {
        val id = nextId++
        log("Request '$method' started")
        val response = transport.send(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                put("params", params)
            },
        )

        val responseId = response["id"]?.jsonPrimitive?.int
        if (responseId != id) {
            throw AssertionError("MCP request '$method' returned response id '$responseId' instead of '$id': $response")
        }
        response["error"]?.let { throw AssertionError("MCP request '$method' failed: $it") }
        log("Request '$method' succeeded")
        return response
    }

    private fun notification(method: String) {
        transport.send(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", method)
                put("params", EMPTY_MCP_PARAMS)
            },
            expectResponseBody = false,
        )
    }

    override fun close() {
        log("Closing")
        transport.close()
    }

    private fun log(message: String) {
        println("[integration-test:mcp] $message")
    }

    companion object {
        fun from(port: Int, projectPath: String): McpHttpClient =
            McpHttpClient(URI.create("http://127.0.0.1:$port"), projectPath)
    }
}

private fun JsonObject.resultObject(): JsonObject = getValue("result").jsonObject
