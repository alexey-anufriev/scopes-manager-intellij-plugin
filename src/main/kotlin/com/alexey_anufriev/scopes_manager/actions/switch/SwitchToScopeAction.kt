package com.alexey_anufriev.scopes_manager.actions.switch

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.openapi.wm.ToolWindow
import com.intellij.psi.search.scope.packageSet.NamedScope

class SwitchToScopeAction(private val scope: NamedScope) : SwitchActionBase(scope.scopeId, scope.icon) {

    override val failureMessage: String = "Failed to switch to scope '${scope.scopeId}'"

    override fun switchView(projectView: ProjectView, toolWindow: ToolWindow) {
        projectView.changeView(ScopeViewPane.ID, scope.scopeId)
        val content = toolWindow.contentManager.contents
            .firstOrNull { it.displayName == scope.scopeId }
        content?.let { toolWindow.contentManager.setSelectedContent(it) }
    }
}
