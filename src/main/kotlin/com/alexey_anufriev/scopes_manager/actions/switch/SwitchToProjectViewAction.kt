package com.alexey_anufriev.scopes_manager.actions.switch

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager

class SwitchToProjectViewAction
    : AnAction("Project", null, AllIcons.General.ProjectStructure) {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(ToolWindowId.PROJECT_VIEW)
            ?: return

        toolWindow.activate({
            try {
                ProjectView.getInstance(project).changeView(ProjectViewPane.ID)
            } catch (t: Throwable) {
                logger<SwitchToProjectViewAction>().warn("Failed to switch to Project view", t)
            }
        }, true, true)
    }
}
