package com.alexey_anufriev.scopes_manager.actions.switch

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder

class SwitchScopeAction : AnAction() {

    override fun update(event: AnActionEvent) {
        val project = event.project
        if (project == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val localScopesManager = NamedScopeManager.getInstance(project)
        val sharedScopesManager = DependencyValidationManager.getInstance(project)
        val hasScopes = localScopesManager.editableScopes.isNotEmpty() || sharedScopesManager.editableScopes.isNotEmpty()

        event.presentation.isEnabledAndVisible = hasScopes
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val localScopesManager = NamedScopeManager.getInstance(project)
        val sharedScopesManager = DependencyValidationManager.getInstance(project)

        val group = DefaultActionGroup()
        group.add(SwitchToProjectViewAction())

        val switchActions = collectSwitchScopeActions(localScopesManager, sharedScopesManager)
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
        ).showCenteredInCurrentWindow(project)
    }

    companion object {
        internal fun collectSwitchScopeActions(
            localScopesManager: NamedScopesHolder,
            sharedScopesManager: NamedScopesHolder
        ): Array<AnAction> {
            val localScopes = localScopesManager.editableScopes
            val sharedScopes = sharedScopesManager.editableScopes

            // Prioritize shared scopes over local ones if they have the same ID
            return (sharedScopes + localScopes)
                .distinctBy { it.scopeId }
                .map { scope -> SwitchToScopeAction(scope) }
                .sortedBy { it.templateText }
                .toTypedArray()
        }
    }
}
