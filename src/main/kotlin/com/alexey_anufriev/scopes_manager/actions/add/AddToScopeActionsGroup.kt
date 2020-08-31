package com.alexey_anufriev.scopes_manager.actions.add

import com.alexey_anufriev.scopes_manager.actions.ScopeGroupActionBase
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import java.util.stream.Stream

class AddToScopeActionsGroup : ScopeGroupActionBase() {

    override fun defineChildItems(
        project: Project,
        event: AnActionEvent,
        localScopesManager: NamedScopesHolder,
        sharedScopesManager: NamedScopesHolder
    ): Array<AnAction> {

        val localScopesActions = Stream.of(*localScopesManager.editableScopes)
            .map { scope -> AddToScopeAction(localScopesManager, scope) }

        val sharedScopesActions = Stream.of(*sharedScopesManager.editableScopes)
            .map { scope -> AddToScopeAction(sharedScopesManager, scope) }

        return Stream.concat(localScopesActions, sharedScopesActions)
            .sorted(compareBy { it.templateText })
            .toArray { size -> arrayOfNulls(size) }
    }

}
