package com.alexey_anufriev.scopes_manager.actions.switch

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.scopeView.NamedScopeFilter
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.ToolWindow
import com.intellij.psi.search.scope.packageSet.NamedScope

class SwitchToScopeAction(private val scope: NamedScope) : SwitchActionBase(scope.scopeId, scope.icon) {

    override val failureMessage: String = "Failed to switch to scope '${scope.scopeId}'"

    override fun switchView(projectView: ProjectView, toolWindow: ToolWindow) {
        val scopePane = projectView.getProjectViewPaneById(ScopeViewPane.ID) as? ScopeViewPane
        val scopeSubId = scopePane?.let { resolveScopeViewSubId(scope, it.getFilters()) }

        if (scopeSubId != null) {
            projectView.changeView(ScopeViewPane.ID, scopeSubId)
        } else {
            logger<SwitchToScopeAction>().warn("Failed to resolve Scope View subId for scope '${scope.scopeId}'")
        }
    }
}

internal fun resolveScopeViewSubId(scope: NamedScope, filters: Iterable<NamedScopeFilter>): String? {
    val matchingFilter = filters.firstOrNull { filter -> filter.scope === scope }
        ?: filters.firstOrNull { filter -> filter.scope.scopeId == scope.scopeId }

    return matchingFilter?.toString()
}
