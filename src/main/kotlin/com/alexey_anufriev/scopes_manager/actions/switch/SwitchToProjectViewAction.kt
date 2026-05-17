package com.alexey_anufriev.scopes_manager.actions.switch

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.wm.ToolWindow

class SwitchToProjectViewAction : SwitchActionBase("Project", AllIcons.General.ProjectStructure) {

    override val failureMessage: String = "Failed to switch to Project view"

    override fun switchView(projectView: ProjectView, toolWindow: ToolWindow) {
        projectView.changeView(ProjectViewPane.ID)
    }
}
