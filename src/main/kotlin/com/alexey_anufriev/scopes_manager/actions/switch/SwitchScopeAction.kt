package com.alexey_anufriev.scopes_manager.actions.switch

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder

class SwitchScopeAction : AnAction(), DumbAware {

    override fun update(event: AnActionEvent) {
        val context = event.switchScopeContext()
        if (context == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = context.hasScopeSwitchTargets
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(event: AnActionEvent) {
        val context = event.switchScopeContext() ?: return

        val group = DefaultActionGroup()
        group.add(SwitchToProjectViewAction())

        val switchActions = collectSwitchScopeActions(
            context.localScopesManager,
            context.sharedScopesManager,
            context.scopeViewPaneAvailable
        )
        if (switchActions.isNotEmpty()) {
            group.add(Separator())
            group.addAll(*switchActions)
        }

        JBPopupFactory.getInstance().createActionGroupPopup(
            "Switch Scope",
            group,
            event.dataContext,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            true,
        ).showCenteredInCurrentWindow(context.project)
    }

    private fun AnActionEvent.switchScopeContext(): SwitchScopeContext? {
        val project = project ?: return null
        return SwitchScopeContext(
            project = project,
            localScopesManager = NamedScopeManager.getInstance(project),
            sharedScopesManager = DependencyValidationManager.getInstance(project),
            scopeViewPaneAvailable = ProjectView.getInstance(project).getProjectViewPaneById(ScopeViewPane.ID) != null
        )
    }

}

private data class SwitchScopeContext(
    val project: Project,
    val localScopesManager: NamedScopesHolder,
    val sharedScopesManager: NamedScopesHolder,
    val scopeViewPaneAvailable: Boolean
) {
    val hasScopes: Boolean
        get() = localScopesManager.editableScopes.isNotEmpty() || sharedScopesManager.editableScopes.isNotEmpty()

    val hasScopeSwitchTargets: Boolean
        get() = scopeViewPaneAvailable && hasScopes
}

internal fun collectSwitchScopeActions(
    localScopesManager: NamedScopesHolder,
    sharedScopesManager: NamedScopesHolder,
    scopeViewPaneAvailable: Boolean = true
): Array<AnAction> {
    if (!scopeViewPaneAvailable) {
        return emptyArray()
    }

    val localScopes = localScopesManager.editableScopes
    val sharedScopes = sharedScopesManager.editableScopes

    // Prioritize shared scopes over local ones if they have the same ID
    return (sharedScopes + localScopes)
        .distinctBy { it.scopeId }
        .map { scope -> SwitchToScopeAction(scope) }
        .sortedBy { it.templateText }
        .toTypedArray()
}
