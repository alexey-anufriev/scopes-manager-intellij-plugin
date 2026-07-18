package com.alexey_anufriev.scopes_manager

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

internal sealed interface LocalScopeDefinition {
    val name: String
}

private data class PatternLocalScope(
    override val name: String,
    val pattern: String
) : LocalScopeDefinition

private data class InvalidLocalScope(
    override val name: String,
    val pattern: String
) : LocalScopeDefinition

internal fun localScope(name: String, pattern: String): LocalScopeDefinition =
    PatternLocalScope(name, pattern)

internal fun emptyLocalScope(name: String): LocalScopeDefinition =
    InvalidLocalScope(name, "")

internal fun Driver.withTemporaryLocalScopes(
    vararg scopeNames: String,
    body: Driver.() -> Unit
) = withTemporaryLocalScopes(
    *scopeNames.map { name -> InvalidLocalScope(name, "file:*") }.toTypedArray(),
    body = body
)

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
