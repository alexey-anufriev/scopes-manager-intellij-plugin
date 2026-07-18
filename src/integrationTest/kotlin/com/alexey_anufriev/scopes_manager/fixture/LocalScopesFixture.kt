package com.alexey_anufriev.scopes_manager.fixture

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.singleProject

@Remote("com.intellij.psi.search.scope.packageSet.NamedScope")
private interface NamedScopeRef

@Remote("com.intellij.psi.search.scope.packageSet.InvalidPackageSet")
private interface InvalidPackageSetRef

@Remote("com.intellij.psi.search.scope.packageSet.FilePatternPackageSet")
private interface FilePatternPackageSetRef

@Remote("com.intellij.psi.search.scope.packageSet.NamedScopeManager")
private interface NamedScopeManagerRef {
    fun setScopes(scopes: Array<NamedScopeRef>)
    fun removeAllSets()
}

/** Describes a local scope to install for the duration of a test. */
internal sealed interface LocalScopeDefinition {
    val name: String
}

/** Defines a local scope backed by a valid file pattern. */
private data class PatternLocalScope(
    override val name: String,
    val pattern: String
) : LocalScopeDefinition

/** Defines an intentionally invalid or empty local scope. */
private data class InvalidLocalScope(
    override val name: String,
    val pattern: String
) : LocalScopeDefinition

/** Creates a local scope definition backed by [pattern]. */
internal fun localScope(name: String, pattern: String): LocalScopeDefinition =
    PatternLocalScope(name, pattern)

/** Creates an empty local scope definition. */
internal fun emptyLocalScope(name: String): LocalScopeDefinition =
    InvalidLocalScope(name, "")

/** Runs [body] with temporary local scopes named by [scopeNames]. */
internal fun Driver.withTemporaryLocalScopes(
    vararg scopeNames: String,
    body: Driver.() -> Unit
) = withTemporaryLocalScopes(
    *scopeNames.map { name -> InvalidLocalScope(name, "file:*") }.toTypedArray(),
    body = body
)

/** Installs [scopeDefinitions], runs [body], and removes the scopes afterward. */
internal fun Driver.withTemporaryLocalScopes(
    vararg scopeDefinitions: LocalScopeDefinition,
    body: Driver.() -> Unit
) {
    val localScopesManager = service<NamedScopeManagerRef>(singleProject())

    try {
        val scopes = withContext(OnDispatcher.EDT) {
            scopeDefinitions.map(::newLocalScope).toTypedArray()
        }
        withWriteAction {
            localScopesManager.setScopes(scopes)
        }
        body()
    } finally {
        withWriteAction {
            localScopesManager.removeAllSets()
        }
    }
}

private fun Driver.newLocalScope(scope: LocalScopeDefinition): NamedScopeRef = when (scope) {
    is PatternLocalScope -> new(
        NamedScopeRef::class,
        scope.name,
        new(FilePatternPackageSetRef::class, null, scope.pattern)
    )
    is InvalidLocalScope -> new(
        NamedScopeRef::class,
        scope.name,
        new(InvalidPackageSetRef::class, scope.pattern)
    )
}
