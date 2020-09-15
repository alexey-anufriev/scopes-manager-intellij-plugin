package com.alexey_anufriev.scopes_manager.actions.add

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class CreateNewScopeAction(
    menuItemLabel: String,
    private val defaultScopeName: String
) : AnAction(menuItemLabel, null, AllIcons.Nodes.ExtractedFolder) {

    override fun actionPerformed(event: AnActionEvent) {
        CreateNewScopeDialog(event, defaultScopeName).show()
    }

}
