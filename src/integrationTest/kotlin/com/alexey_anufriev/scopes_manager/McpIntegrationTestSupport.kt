package com.alexey_anufriev.scopes_manager

import com.intellij.driver.client.Remote
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
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

const val MCP_PLUGIN_ID = "com.intellij.mcpServer"

@Remote("com.intellij.execution.configurations.GeneralCommandLine")
interface GeneralCommandLineRef {
    fun getExePath(): String
    fun getParametersList(): ParametersListRef
}

@Remote("com.intellij.execution.configurations.ParametersList")
interface ParametersListRef {
    fun getParameters(): List<String>
}

class McpStdioClient private constructor(
    private val process: Process,
) : Closeable {
    private val reader = BufferedReader(InputStreamReader(process.inputStream))
    private val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
    private var nextId = 1

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
        writeJson(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                put("params", params)
            },
        )

        while (true) {
            val response = readJson()
            val responseId = response["id"]?.jsonPrimitive?.int
            if (responseId == id) {
                response["error"]?.let { throw AssertionError("MCP request '$method' failed: $it") }
                return response
            }
        }
    }

    private fun notification(method: String, params: JsonObject = EMPTY_PARAMS) {
        writeJson(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", method)
                put("params", params)
            },
        )
    }

    private fun writeJson(value: JsonObject) {
        json.encodeToString(JsonObject.serializer(), value).also { line ->
            writer.write(line)
            writer.newLine()
            writer.flush()
        }
    }

    private fun readJson(): JsonObject {
        val line = reader.readLine() ?: throw AssertionError("MCP stdio process exited before sending a response")
        return json.parseToJsonElement(line).jsonObject
    }

    override fun close() {
        process.destroy()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val EMPTY_PARAMS: JsonObject = buildJsonObject {}

        fun from(command: GeneralCommandLineRef, port: Int, projectPath: String): McpStdioClient {
            val process = ProcessBuilder(listOf(command.getExePath()) + command.getParametersList().getParameters())
                .apply {
                    environment()["IJ_MCP_SERVER_PORT"] = port.toString()
                    environment()["IJ_MCP_SERVER_PROJECT_PATH"] = projectPath
                }
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
            return McpStdioClient(process)
        }

        private fun JsonObject.resultObject(): JsonObject =
            getValue("result").jsonObject
    }
}
