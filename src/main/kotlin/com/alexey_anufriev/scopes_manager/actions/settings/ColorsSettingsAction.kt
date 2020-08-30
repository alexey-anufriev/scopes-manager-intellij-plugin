package com.alexey_anufriev.scopes_manager.actions.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

class ColorsSettingsAction : AnAction("Scopes Colors", null, AllIcons.Actions.Colors) {

    override fun actionPerformed(event: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(event.project, "File Colors")
    }

}
