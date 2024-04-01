package com.alexey_anufriev.scopes_manager.actions

import com.alexey_anufriev.scopes_manager.utils.ActionUtils.hideAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import java.util.Arrays

abstract class ScopeGroupActionBase : DefaultActionGroup() {

    /**
     * Checks if the action group is eligible to be displayed
     */
    override fun update(event: AnActionEvent) {
        val project = event.project

        if (project == null) {
            hideAction(event)
            return
        }

        val fileIndex = ProjectRootManager.getInstance(project).fileIndex

        val selection = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (selection == null) {
            hideAction(event)
            return
        }

        val selectionContainsNoProjectFiles = Arrays.stream(selection)
            .map(fileIndex::isInContent)
            .noneMatch { it }

        if (selectionContainsNoProjectFiles) {
            hideAction(event)
        }
    }

    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
        if (event == null) {
            return emptyArray()
        }

        val project = event.project ?: return emptyArray()
        val localScopesManager = NamedScopeManager.getInstance(project)
        val sharedScopesManager = DependencyValidationManager.getInstance(project)

        return defineChildItems(project, event, localScopesManager, sharedScopesManager)
    }

    protected abstract fun defineChildItems(
        project: Project,
        event: AnActionEvent,
        localScopesManager: NamedScopesHolder,
        sharedScopesManager: NamedScopesHolder
    ): Array<AnAction>

}
