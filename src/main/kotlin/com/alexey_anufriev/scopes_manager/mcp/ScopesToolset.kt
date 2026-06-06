package com.alexey_anufriev.scopes_manager.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootProperties

class ScopesToolset : McpToolset {

    @McpTool(name = "get_scopes")
    @McpDescription("Return the names of all user-defined IntelliJ scopes (local + shared), sorted alphabetically.")
    suspend fun getScopes(): String {
        val context = currentCoroutineContext()
        context.reportToolActivity("Collecting IntelliJ scopes")
        val project = context.project

        val names = readAction {
            val holders = project.scopeHolders()
            collectScopeNames(holders.local, holders.shared)
        }

        return if (names.isEmpty()) "No user-defined scopes in this project." else names.joinToString("\n")
    }

    @McpTool(name = "get_scope_files")
    @McpDescription(
        "Return the files and folders that comprise a named IntelliJ scope. " +
                "Recursive folders are shown with a trailing '/'. Takes one argument: 'name' (scope name)."
    )
    suspend fun getScopeFiles(
        @McpDescription("Name of the IntelliJ scope to inspect.")
        name: String
    ): String {
        val context = currentCoroutineContext()
        context.reportToolActivity("Resolving files for scope '$name'")
        val project = context.project

        val relativePats = readAction {
            val packageSet = project.scopeHolders().findScope(name)?.value ?: return@readAction null
            ScopePatternParser.parse(packageSet, project.filePatternResolver())
        } ?: mcpFail("Scope not found: $name")

        return relativePats.joinToString("\n")
    }

}

private data class ScopeHolders(
    val local: NamedScopesHolder,
    val shared: NamedScopesHolder,
) {
    fun findScope(name: String): NamedScope? =
        local.getScope(name) ?: shared.getScope(name)
}

private fun Project.scopeHolders() = ScopeHolders(
    local = NamedScopeManager.getInstance(this),
    shared = DependencyValidationManager.getInstance(this),
)

private fun Project.filePatternResolver(): FilePatternResolver {
    val projectRoot = guessProjectDir()
    val moduleManager = ModuleManager.getInstance(this)

    return resolver@{ modulePattern, pattern ->
        if (modulePattern.isNullOrEmpty() || projectRoot == null) {
            return@resolver listOf(pattern)
        }
        val module = moduleManager.findModuleByName(modulePattern) ?: return@resolver listOf(pattern)

        val contentRootPaths = ModuleRootManager.getInstance(module).contentEntries
            .filter { it.hasUserSources() }
            .mapNotNull { it.file }
            .mapNotNull { VfsUtilCore.getRelativePath(it, projectRoot) }

        when {
            contentRootPaths.isEmpty() -> listOf(pattern)
            else -> contentRootPaths.map { path -> if (path.isEmpty()) pattern else "$path/$pattern" }
        }
    }
}

private fun ContentEntry.hasUserSources(): Boolean {
    val folders = sourceFolders
    return folders.isEmpty() || folders.any { !it.isForGeneratedSources() }
}

private fun SourceFolder.isForGeneratedSources(): Boolean {
    return when (val props = jpsElement.properties) {
        is JavaSourceRootProperties -> props.isForGeneratedSources
        is JavaResourceRootProperties -> props.isForGeneratedSources
        else -> false
    }
}

internal fun collectScopeNames(
    localScopesManager: NamedScopesHolder,
    sharedScopesManager: NamedScopesHolder
): List<String> =
    (localScopesManager.editableScopes + sharedScopesManager.editableScopes)
        .map(NamedScope::getScopeId)
        .distinct()
        .sorted()
