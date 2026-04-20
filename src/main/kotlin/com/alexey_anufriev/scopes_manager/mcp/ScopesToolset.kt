package com.alexey_anufriev.scopes_manager.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import java.util.stream.Collectors.toList
import java.util.stream.Stream
import kotlin.coroutines.coroutineContext

class ScopesToolset : McpToolset {

    @McpTool(name = "list_scopes")
    @McpDescription("List names of all user-defined IntelliJ scopes (local + shared), sorted alphabetically.")
    suspend fun listScopes(): String {
        val project = coroutineContext.project

        return readAction {
            val localScopesManager = NamedScopeManager.getInstance(project)
            val sharedScopesManager = DependencyValidationManager.getInstance(project)

            collectScopeNames(localScopesManager, sharedScopesManager)
                .joinToString("\n")
        }
    }

    @McpTool(name = "list_files_in_scope")
    @McpDescription(
        "List the files and folders that comprise a named IntelliJ scope. " +
        "Recursive folders are shown with a trailing '/'. Takes one argument: 'name' (scope name)."
    )
    suspend fun listFilesInScope(
        @McpDescription("Name of the IntelliJ scope to inspect.")
        name: String
    ): String {
        val project = coroutineContext.project

        val pattern = readAction { findScopePattern(project, name) }
            ?: return "Scope not found: $name"

        return ScopePatternParser.parse(pattern).joinToString("\n")
    }

    private fun collectScopeNames(
        localScopesManager: NamedScopesHolder,
        sharedScopesManager: NamedScopesHolder
    ): List<String> {
        val allEditableScopes = arrayOf(
            *localScopesManager.editableScopes,
            *sharedScopesManager.editableScopes
        )

        return Stream.of(*allEditableScopes)
            .map(NamedScope::getScopeId)
            .distinct()
            .sorted()
            .collect(toList())
    }

    private fun findScopePattern(project: Project, name: String): String? {
        val scope = NamedScopeManager.getInstance(project).getScope(name)
            ?: DependencyValidationManager.getInstance(project).getScope(name)
            ?: return null

        return scope.value?.text
    }

}
