package com.alexey_anufriev.scopes_manager.actions.settings

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup

class ScopeSettingsActionsGroup : DefaultActionGroup() {

    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
        return arrayOf(ScopesSettingsAction(), ColorsSettingsAction())
    }

}
