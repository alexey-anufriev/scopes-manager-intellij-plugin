package com.alexey_anufriev.scopes_manager.actions.remove

import com.alexey_anufriev.scopes_manager.actions.ScopeGroupActionBase
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.psi.search.scope.packageSet.PackageSetBase
import java.util.stream.Stream

class RemoveFromScopeActionsGroup : ScopeGroupActionBase() {

    override fun defineChildItems(
        project: Project,
        event: AnActionEvent,
        localScopesManager: NamedScopesHolder,
        sharedScopesManager: NamedScopesHolder
    ): Array<AnAction> {

        val eventData = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return emptyArray()
        val selectedFiles = listOf(*eventData)
        val assignedLocalScopes = mapScopesToActions(project, localScopesManager, selectedFiles)
        val assignedSharedScopes = mapScopesToActions(project, sharedScopesManager, selectedFiles)

        return Stream.concat(assignedLocalScopes, assignedSharedScopes)
            .sorted(compareBy { it.templateText })
            .toArray { size -> arrayOfNulls(size) }
    }

    private fun mapScopesToActions(
        project: Project,
        scopesHolder: NamedScopesHolder,
        selectedFiles: List<VirtualFile>
    ): Stream<RemoveFromScopeAction> {

        return Stream.of(*scopesHolder.editableScopes)
            .filter { scope ->
                val packageSet = scope.value as? PackageSetBase ?: return@filter false
                selectedFiles.stream().anyMatch { file -> packageSet.contains(file, project, scopesHolder) }
            }
            .map { scope -> RemoveFromScopeAction(scopesHolder, scope) }
    }

}
