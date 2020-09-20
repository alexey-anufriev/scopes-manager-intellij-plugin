package com.alexey_anufriev.scopes_manager.actions.clear

import com.alexey_anufriev.scopes_manager.actions.ScopeGroupActionBase
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import org.apache.commons.lang.StringUtils
import org.jetbrains.annotations.NotNull
import java.util.stream.Stream

class ClearScopeActionGroup : ScopeGroupActionBase() {

    override fun defineChildItems(
        project: Project,
        event: AnActionEvent,
        localScopesManager: NamedScopesHolder,
        sharedScopesManager: NamedScopesHolder
    ): Array<AnAction> {

        val localScopesActions = Stream.of(*localScopesManager.editableScopes)
            .filter { isScopeNotEmpty(it) }
            .map { scope -> ClearScopeAction(localScopesManager, scope) }

        val sharedScopesActions = Stream.of(*sharedScopesManager.editableScopes)
            .filter { isScopeNotEmpty(it) }
            .map { scope -> ClearScopeAction(sharedScopesManager, scope) }

        return Stream.concat(localScopesActions, sharedScopesActions)
            .sorted(compareBy { it.templateText })
            .toArray { size -> arrayOfNulls(size) }
    }

    private fun isScopeNotEmpty(scope: @NotNull NamedScope): Boolean {
        return scope.value != null && StringUtils.isNotEmpty(scope.value!!.text)
    }

}


