package com.alexey_anufriev.scopes_manager.actions.add

import com.alexey_anufriev.scopes_manager.actions.ScopeGroupActionBase
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.tasks.LocalTask
import com.intellij.tasks.TaskManager
import com.intellij.tasks.impl.TaskManagerImpl
import java.util.stream.Stream

class AddToScopeActionsGroup : ScopeGroupActionBase() {

    override fun defineChildItems(
        project: Project,
        event: AnActionEvent,
        localScopesManager: NamedScopesHolder,
        sharedScopesManager: NamedScopesHolder
    ): Array<AnAction> {

        return arrayOf(
            CreateNewScopeAction("Create New...", "New Scope"),
            Separator(),
            *getActionsForTaskManager(project, localScopesManager, sharedScopesManager),
            Separator(),
            *getActionsForScopes(localScopesManager, sharedScopesManager)
        )
    }

    private fun getActionsForScopes(
        localScopesManager: NamedScopesHolder,
        sharedScopesManager: NamedScopesHolder
    ): Array<AnAction> {

        val localScopesActions = Stream.of(*localScopesManager.editableScopes)
            .map { scope -> AddToScopeAction(localScopesManager, scope) }

        val sharedScopesActions = Stream.of(*sharedScopesManager.editableScopes)
            .map { scope -> AddToScopeAction(sharedScopesManager, scope) }

        return Stream.concat(localScopesActions, sharedScopesActions)
            .sorted(compareBy { it.templateText })
            .toArray { size -> arrayOfNulls(size) }
    }

    private fun getActionsForTaskManager(
        project: Project,
        localScopesManager: NamedScopesHolder,
        sharedScopesManager: NamedScopesHolder
    ): Array<AnAction> {

        val taskManager = TaskManager.getManager(project)

        if (taskManager != null && isTaskManagerInUse(taskManager)) {
            val availableScopes = arrayOf(
                *localScopesManager.editableScopes,
                *sharedScopesManager.editableScopes
            ).map { it.scopeId }

            return taskManager.getLocalTasks(false).stream()
                .filter { !availableScopes.contains(buildScopeName(it.presentableName)) }
                .sorted(TaskManagerImpl.TASK_UPDATE_COMPARATOR)
                .sorted(compareByDescending { it.isActive })
                .map { createActionForTask(it) }
                .toArray { size -> arrayOfNulls(size) }
        }

        return emptyArray()
    }

    private fun createActionForTask(task: LocalTask): AnAction {
        return CreateNewScopeAction(
            "Create New for Task ${task.presentableName}",
            buildScopeName(task.presentableName)
        )
    }

    private fun buildScopeName(taskName: String): String {
        return "Task $taskName"
    }

    private fun isTaskManagerInUse(taskManager: TaskManager): Boolean {
        val activeTask = taskManager.activeTask
        return !activeTask.isDefault || !Comparing.equal(activeTask.created, activeTask.updated)
    }

}
