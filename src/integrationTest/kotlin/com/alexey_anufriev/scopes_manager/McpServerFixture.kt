package com.alexey_anufriev.scopes_manager

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.client.utility
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal const val MCP_PLUGIN_ID = "com.intellij.mcpServer"

@Remote("com.intellij.openapi.extensions.PluginId")
private interface PluginIdRef

@Remote("com.intellij.openapi.extensions.PluginId")
private interface PluginIdUtilRef {
    fun getId(idString: String): PluginIdRef
}

@Remote("com.intellij.ide.plugins.PluginManagerCore")
private interface PluginManagerCoreRef {
    fun isPluginInstalled(id: PluginIdRef): Boolean
    fun isLoaded(id: PluginIdRef): Boolean
    fun isDisabled(id: PluginIdRef): Boolean
}

@Remote("com.intellij.mcpserver.impl.McpServerService", plugin = MCP_PLUGIN_ID)
private interface McpServerServiceRef {
    fun start()
    fun stop()
    fun isRunning(): Boolean
    fun getPort(): Int
}

internal fun Driver.withMcpClient(
    projectPath: String,
    body: McpHttpClient.() -> Unit,
) {
    assertMcpPluginEnabled()
    val server = service<McpServerServiceRef>()
    logMcpServerCheckpoint("Starting MCP server")
    server.start()
    try {
        McpHttpClient.from(server.getPort(), projectPath).use { client ->
            client.body()
        }
    } finally {
        logMcpServerCheckpoint("Stopping MCP server")
        server.stop()
        waitUntil(
            description = "MCP server to stop",
            timeout = 5.seconds,
            interval = 100.milliseconds,
        ) {
            !server.isRunning()
        }
    }
}

private fun Driver.assertMcpPluginEnabled() {
    val pluginId = utility<PluginIdUtilRef>().getId(MCP_PLUGIN_ID)
    val pluginManager = utility<PluginManagerCoreRef>()

    assertMcpPluginState(
        pluginManager.isPluginInstalled(pluginId),
        "installed",
    )
    assertMcpPluginState(
        !pluginManager.isDisabled(pluginId),
        "enabled",
    )
    assertMcpPluginState(
        pluginManager.isLoaded(pluginId),
        "loaded",
    )
}

private fun assertMcpPluginState(actual: Boolean, expectedState: String) {
    if (!actual) {
        throw AssertionError("Bundled MCP Server plugin '$MCP_PLUGIN_ID' must be $expectedState in this IDE")
    }
}

private fun logMcpServerCheckpoint(message: String) {
    println("[integration-test:mcp-server] $message")
}
