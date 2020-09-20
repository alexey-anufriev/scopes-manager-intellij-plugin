package com.alexey_anufriev.scopes_manager.actions.clear

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.psi.search.scope.packageSet.InvalidPackageSet
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import java.util.stream.Collectors
import java.util.stream.Stream


class ClearScopeAction(
    private val scopesHolder: NamedScopesHolder,
    private val scope: NamedScope
) : AnAction(scope.name, null, scope.icon) {

    override fun actionPerformed(event: AnActionEvent) {
        val emptyScope = NamedScope(scope.name, scope.icon, InvalidPackageSet(""))

        val unchangedScopes = Stream.of(*scopesHolder.editableScopes)
            .filter { editableScope -> editableScope.name != templateText }
            .collect(Collectors.toList())

        val newScopes = unchangedScopes.toTypedArray().plus(emptyScope)

        scopesHolder.scopes = newScopes

        val project = event.project!!
        ProjectView.getInstance(project).refresh()

        val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
        for (openFile in fileEditorManager.openFiles) {
            fileEditorManager.updateFilePresentation(openFile)
        }
    }

}
