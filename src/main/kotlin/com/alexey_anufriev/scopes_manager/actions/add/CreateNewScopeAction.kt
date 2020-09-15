package com.alexey_anufriev.scopes_manager.actions.add

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class CreateNewScopeAction : AnAction("Create New...", null, AllIcons.Nodes.ExtractedFolder) {

    override fun actionPerformed(event: AnActionEvent) {
        CreateNewScopeDialog(event).show()
    }

}
