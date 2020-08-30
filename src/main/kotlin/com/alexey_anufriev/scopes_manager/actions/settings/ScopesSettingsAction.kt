package com.alexey_anufriev.scopes_manager.actions.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

class ScopesSettingsAction : AnAction("Scopes List", null, AllIcons.Actions.ListFiles) {

    override fun actionPerformed(event: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(event.project, "Scopes")
    }

}
