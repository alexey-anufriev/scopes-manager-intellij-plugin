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
import java.util.stream.Stream

class SwitchScopeAction : AnAction() {

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val localScopesManager = NamedScopeManager.getInstance(project)
        val sharedScopesManager = DependencyValidationManager.getInstance(project)

        val group = DefaultActionGroup(
            SwitchToProjectViewAction(),
            Separator(),
            *collectSwitchScopeActions(localScopesManager, sharedScopesManager),
        )

        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                "Switch Scope",
                group,
                event.dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true,
            )
            .showCenteredInCurrentWindow(project)
    }

    companion object {
        internal fun collectSwitchScopeActions(
            localScopesManager: NamedScopesHolder,
            sharedScopesManager: NamedScopesHolder
        ): Array<AnAction> {
            val localScopesActions = Stream.of(*localScopesManager.editableScopes)
                .map { scope -> SwitchToScopeAction(scope) }
            val sharedScopesActions = Stream.of(*sharedScopesManager.editableScopes)
                .map { scope -> SwitchToScopeAction(scope) }
            return Stream.concat(localScopesActions, sharedScopesActions)
                .sorted(compareBy { it.templateText })
                .toArray { size -> arrayOfNulls(size) }
        }
    }
}
