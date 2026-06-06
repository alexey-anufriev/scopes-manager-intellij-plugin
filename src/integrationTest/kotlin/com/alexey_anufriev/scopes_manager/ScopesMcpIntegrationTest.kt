package com.alexey_anufriev.scopes_manager

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.client.utility
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.singleProject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScopesMcpIntegrationTest : UiIntegrationTestSupport() {

    @Remote("com.intellij.openapi.extensions.PluginId")
    interface PluginIdRef

    @Remote("com.intellij.openapi.extensions.PluginId")
    interface PluginIdUtilRef {
        fun getId(idString: String): PluginIdRef
    }

    @Remote("com.intellij.ide.plugins.PluginManagerCore")
    interface PluginManagerCoreRef {
        fun isPluginInstalled(id: PluginIdRef): Boolean
        fun isLoaded(id: PluginIdRef): Boolean
        fun isDisabled(id: PluginIdRef): Boolean
    }

    @Remote("com.intellij.mcpserver.impl.McpServerService", plugin = MCP_PLUGIN_ID)
    interface McpServerServiceRef {
        fun start()
        fun stop()
        fun isRunning(): Boolean
        fun getPort(): Int
    }

    @Remote("com.intellij.psi.search.scope.packageSet.NamedScope")
    interface NamedScopeRef

    @Remote("com.intellij.psi.search.scope.packageSet.FilePatternPackageSet")
    interface FilePatternPackageSetRef

    @Remote("com.intellij.psi.search.scope.packageSet.InvalidPackageSet")
    interface InvalidPackageSetRef

    @Remote("com.intellij.psi.search.scope.packageSet.NamedScopeManager")
    interface NamedScopeManagerRef {
        fun setScopes(scopes: Array<NamedScopeRef>)
        fun removeAllSets()
    }

    @Test
    fun mcpClientCanListAndCallScopeTools() {
        val config = readConfig()

        try {
            runUiTest { uiConfig ->
                handleLicenseDialogIfShown()
                waitForUiReady(uiConfig.productCode, uiConfig.toolWindowId)
                assertMcpServerPluginEnabled()

                withTemporaryLocalScopes(
                    newLocalScope("MCP Recursive", "mcp/sample//*"),
                    newLocalScope("MCP Non Recursive", "mcp/sample/*"),
                    newEmptyLocalScope("MCP Empty"),
                ) {
                    val server = service<McpServerServiceRef>()
                    server.start()
                    try {
                        McpHttpClient.from(
                            port = server.getPort(),
                            projectPath = uiConfig.projectHome.toAbsolutePath().toString(),
                        ).use { client ->
                            client.initialize()

                            val toolNames = client.listToolNames()
                            assertTrue(toolNames.contains("get_scopes"), "MCP tools did not include get_scopes: $toolNames")
                            assertTrue(toolNames.contains("get_scope_files"), "MCP tools did not include get_scope_files: $toolNames")

                            assertEquals(
                                listOf("MCP Empty", "MCP Non Recursive", "MCP Recursive").joinToString("\n"),
                                client.callTextTool("get_scopes"),
                            )
                            assertEquals(
                                "mcp/sample/",
                                client.callTextTool("get_scope_files", mapOf("name" to "MCP Recursive")),
                            )
                            assertEquals(
                                "mcp/sample/*",
                                client.callTextTool("get_scope_files", mapOf("name" to "MCP Non Recursive")),
                            )
                            assertEquals(
                                "Scope 'MCP Empty' is empty.",
                                client.callTextTool("get_scope_files", mapOf("name" to "MCP Empty")),
                            )
                        }
                    } finally {
                        server.stop()
                        waitForMcpServerStopped(server)
                    }
                }
            }
        } catch (throwable: Throwable) {
            skipUnavailableEap(config, throwable)
            throw throwable
        }
    }

    override fun testContextName(): String = "scopes-manager-mcp-integration"

    private fun waitForMcpServerStopped(server: McpServerServiceRef) {
        repeat(50) {
            if (!server.isRunning()) {
                return
            }
            Thread.sleep(100)
        }
    }

    private fun Driver.assertMcpServerPluginEnabled() {
        val pluginId = utility<PluginIdUtilRef>().getId(MCP_PLUGIN_ID)
        val pluginManager = utility<PluginManagerCoreRef>()

        assertTrue(
            pluginManager.isPluginInstalled(pluginId),
            "Bundled MCP Server plugin '$MCP_PLUGIN_ID' must be installed in this IDE",
        )
        assertFalse(
            pluginManager.isDisabled(pluginId),
            "Bundled MCP Server plugin '$MCP_PLUGIN_ID' must be enabled in this IDE",
        )
        assertTrue(
            pluginManager.isLoaded(pluginId),
            "Bundled MCP Server plugin '$MCP_PLUGIN_ID' must be loaded in this IDE",
        )
    }

    private fun Driver.withTemporaryLocalScopes(vararg scopes: NamedScopeRef, body: Driver.() -> Unit) {
        val project = singleProject()
        val localScopesManager = service<NamedScopeManagerRef>(project)

        try {
            withWriteAction {
                localScopesManager.setScopes(scopes.toList().toTypedArray())
            }
            body()
        } finally {
            withWriteAction {
                localScopesManager.removeAllSets()
            }
        }
    }

    private fun Driver.newLocalScope(scopeName: String, pattern: String): NamedScopeRef =
        withContext(OnDispatcher.EDT) {
            new(
                NamedScopeRef::class,
                scopeName,
                new(FilePatternPackageSetRef::class, null, pattern),
            )
        }

    private fun Driver.newEmptyLocalScope(scopeName: String): NamedScopeRef =
        withContext(OnDispatcher.EDT) {
            new(
                NamedScopeRef::class,
                scopeName,
                new(InvalidPackageSetRef::class, ""),
            )
        }

}
