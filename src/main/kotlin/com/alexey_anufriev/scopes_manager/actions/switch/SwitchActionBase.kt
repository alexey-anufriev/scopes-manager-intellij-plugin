package com.alexey_anufriev.scopes_manager.actions.switch

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.Icon

abstract class SwitchActionBase(text: String, icon: Icon?) : AnAction(text, null, icon) {

    protected abstract val failureMessage: String

    protected abstract fun switchView(projectView: ProjectView, toolWindow: ToolWindow)

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW) ?: return

        toolWindow.activate({
            try {
                switchView(ProjectView.getInstance(project), toolWindow)
            } catch (t: Throwable) {
                logger<SwitchActionBase>().warn(failureMessage, t)
            }
        }, true, true)
    }

}
