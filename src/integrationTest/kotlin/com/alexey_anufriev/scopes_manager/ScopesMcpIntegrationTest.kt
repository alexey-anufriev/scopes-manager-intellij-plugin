package com.alexey_anufriev.scopes_manager

import com.alexey_anufriev.scopes_manager.fixture.emptyLocalScope
import com.alexey_anufriev.scopes_manager.fixture.localScope
import com.alexey_anufriev.scopes_manager.fixture.withMcpClient
import com.alexey_anufriev.scopes_manager.fixture.withTemporaryLocalScopes
import com.alexey_anufriev.scopes_manager.support.IdeIntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScopesMcpIntegrationTest : IdeIntegrationTestSupport() {

    @Test
    fun mcpClientCanListAndCallScopeTools() {
        logTestCheckpoint("MCP integration test started")
        ideTest { ideConfig ->
            withTemporaryLocalScopes(
                localScope("MCP Recursive", "mcp/sample//*"),
                localScope("MCP Non Recursive", "mcp/sample/*"),
                emptyLocalScope("MCP Empty"),
            ) {
                withMcpClient(ideConfig.projectHome.toAbsolutePath().toString()) {
                    initialize()

                    val toolNames = listToolNames()
                    assertTrue(toolNames.contains("get_scopes"), "MCP tools did not include get_scopes: $toolNames")
                    assertTrue(toolNames.contains("get_scope_files"), "MCP tools did not include get_scope_files: $toolNames")

                    assertEquals(
                        listOf("MCP Empty", "MCP Non Recursive", "MCP Recursive").joinToString("\n"),
                        callTextTool("get_scopes"),
                    )
                    assertEquals(
                        "mcp/sample/",
                        callTextTool("get_scope_files", mapOf("name" to "MCP Recursive")),
                    )
                    assertEquals(
                        "mcp/sample/*",
                        callTextTool("get_scope_files", mapOf("name" to "MCP Non Recursive")),
                    )
                    assertEquals(
                        "Scope 'MCP Empty' is empty.",
                        callTextTool("get_scope_files", mapOf("name" to "MCP Empty")),
                    )
                }
            }
        }
    }

    override fun testContextName(): String = "scopes-manager-mcp-integration"

}
