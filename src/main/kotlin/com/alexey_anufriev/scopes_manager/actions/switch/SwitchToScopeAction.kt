package com.alexey_anufriev.scopes_manager.actions.switch

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.search.scope.packageSet.NamedScope

class SwitchToScopeAction(private val scope: NamedScope)
    : AnAction(scope.scopeId, null, scope.icon) {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(ToolWindowId.PROJECT_VIEW)
            ?: return

        toolWindow.activate({
            try {
                ProjectView.getInstance(project).changeView(ScopeViewPane.ID, scope.scopeId)
                val content = toolWindow.contentManager.contents
                    .firstOrNull { it.displayName == scope.scopeId }
                content?.let { toolWindow.contentManager.setSelectedContent(it) }
            } catch (t: Throwable) {
                logger<SwitchToScopeAction>().warn("Failed to switch to scope '${scope.scopeId}'", t)
            }
        }, true, true)
    }
}
