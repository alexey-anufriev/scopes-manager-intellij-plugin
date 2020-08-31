package com.alexey_anufriev.scopes_manager.actions

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.packageDependencies.ui.DirectoryNode
import com.intellij.packageDependencies.ui.FileNode
import com.intellij.packageDependencies.ui.ModuleNode
import com.intellij.packageDependencies.ui.PatternDialectProvider
import com.intellij.packageDependencies.ui.ProjectPatternProvider
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.psi.search.scope.packageSet.PackageSet
import com.intellij.psi.search.scope.packageSet.PackageSetBase
import java.util.Arrays
import java.util.stream.Collectors
import java.util.stream.Stream

abstract class ScopeActionBase(
    protected val scopesHolder: NamedScopesHolder,
    scope: NamedScope
) : AnAction(scope.name, null, scope.icon) {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val projectManager = ProjectRootManager.getInstance(project)
        val patternProvider = PatternDialectProvider.getInstance(ProjectPatternProvider.FILE)

        val unchangedScopes = Stream.of(*scopesHolder.editableScopes)
            .filter { editableScope -> editableScope.name != templateText }
            .collect(Collectors.toList())

        Arrays.stream(event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY))
            .filter { file -> projectManager.fileIndex.getContentRootForFile(file) != null }
            .forEach { file -> performScopeAction(project, projectManager, patternProvider, unchangedScopes, file) }

        ProjectView.getInstance(event.project).refresh()
    }

    private fun performScopeAction(
        project: Project,
        projectManager: ProjectRootManager,
        patternProvider: PatternDialectProvider,
        unchangedScopes: List<NamedScope>,
        selectedFile: VirtualFile
    ) {
        val currentScopeContent = scopesHolder.getScope(templateText)!!.value as? PackageSetBase

        if (skipAction(project, selectedFile, currentScopeContent)) {
            return
        }

        val module = projectManager.fileIndex.getModuleForFile(selectedFile) ?: return

        val selectedNode = if (selectedFile.isDirectory) {
            DirectoryNode(selectedFile, project, false, false, project.baseDir, projectManager.contentRoots)
        } else {
            FileNode(selectedFile, project, false)
        }

        selectedNode.setParent(ModuleNode(module, null))

        val selectedPackageContent = if (selectedFile.isDirectory) {
            patternProvider.createPackageSet(selectedNode, true)
        } else {
            patternProvider.createPackageSet(selectedNode, false)
        }

        if (selectedPackageContent == null) {
            return
        }

        val newScopeContent = getNewScopeContent(project, selectedFile, currentScopeContent, selectedPackageContent)
        val scopes = unchangedScopes.toTypedArray()
        scopes.plus(NamedScope(templatePresentation.text, templatePresentation.icon, newScopeContent))
        scopesHolder.scopes = scopes

        FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(selectedFile)
    }

    /**
     * Returns modified content for the scope defined by the concrete action implementation
     */
    protected abstract fun getNewScopeContent(
        project: Project,
        selectedFile: VirtualFile,
        currentScopeContent: PackageSetBase?,
        selectedPackageContent: PackageSet
    ): PackageSetBase

    protected abstract fun skipAction(
        project: Project,
        selectedFile: VirtualFile,
        currentScopeContent: PackageSetBase?
    ): Boolean

}
