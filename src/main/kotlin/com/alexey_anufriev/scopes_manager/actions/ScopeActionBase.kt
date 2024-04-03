package com.alexey_anufriev.scopes_manager.actions

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId.PROJECT_VIEW
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.packageDependencies.ui.DirectoryNode
import com.intellij.packageDependencies.ui.FileNode
import com.intellij.packageDependencies.ui.ModuleNode
import com.intellij.packageDependencies.ui.PatternDialectProvider
import com.intellij.packageDependencies.ui.ProjectPatternProvider
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.psi.search.scope.packageSet.PackageSet
import com.intellij.psi.search.scope.packageSet.PackageSetBase
import com.intellij.util.concurrency.EdtExecutorService
import java.util.Arrays
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import java.util.stream.Stream

abstract class ScopeActionBase(
    protected val scopesHolder: NamedScopesHolder,
    scope: NamedScope
) : AnAction(scope.scopeId, null, scope.icon) {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project!!
        val files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)!!
        processFiles(project, files)
    }

    fun processFiles(project: Project, files: Array<VirtualFile>) {
        val projectManager = ProjectRootManager.getInstance(project)
        val patternProvider = PatternDialectProvider.getInstance(ProjectPatternProvider.FILE)

        val unchangedScopes = Stream.of(*scopesHolder.editableScopes)
            .filter { editableScope -> editableScope.scopeId != templateText }
            .collect(Collectors.toList())

        Arrays.stream(files)
            .filter { file -> projectManager.fileIndex.isInContent(file) }
            .forEach { file -> performScopeAction(project, projectManager, patternProvider, unchangedScopes, file) }

        // save and restore view (e.g. when a certain scope was selected)
        val contentManager = ToolWindowManager.getInstance(project).getToolWindow(PROJECT_VIEW)?.contentManager
        val projectView = ProjectView.getInstance(project)
        val viewId = projectView.currentProjectViewPane.id
        var selectScopeName: String? = null

        if (viewId == ScopeViewPane.ID && contentManager != null) {
            selectScopeName = contentManager.selectedContent?.displayName
        }

        // switch to main project tree for global refresh first
        projectView.changeView(ProjectViewPane.ID)
        projectView.refresh()

        // then switch back to scope that was selected
        if (selectScopeName != null && contentManager != null) {
            val restoreScopeAction: () -> Unit = {
                val selectedScope = contentManager.contents.first { content -> content.displayName == selectScopeName }
                contentManager.setSelectedContent(selectedScope)
            }

            // wait for a delay to let project view perform a refresh after scope content change
            EdtExecutorService.getScheduledExecutorInstance().schedule(restoreScopeAction, 25, TimeUnit.MILLISECONDS);
        }
    }

    private fun performScopeAction(
        project: Project,
        projectManager: ProjectRootManager,
        patternProvider: PatternDialectProvider?,
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

        var selectedContent = if (selectedFile.isDirectory) {
            patternProvider?.createPackageSet(selectedNode, true)
        } else {
            patternProvider?.createPackageSet(selectedNode, false)
        }

        // [start] support Rider
        if (selectedContent == null) {
            var filePattern = VfsUtilCore.getRelativePath(selectedFile, project.baseDir, '/')
            if (filePattern != null) {
                if (selectedFile.isDirectory) {
                    filePattern += "/*";
                }
                selectedContent = FilePatternPackageSet(null, filePattern)
            }
        }
        // [end] support Rider

        if (selectedContent == null) {
            return
        }

        val newScopeContent = getNewScopeContent(project, selectedFile, currentScopeContent, selectedContent)
        val updatedScope = NamedScope(templatePresentation.text, templatePresentation.icon, newScopeContent)
        val scopes = unchangedScopes.toTypedArray().plus(updatedScope)
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
        selectedContent: PackageSet
    ): PackageSetBase

    protected abstract fun skipAction(
        project: Project,
        selectedFile: VirtualFile,
        currentScopeContent: PackageSetBase?
    ): Boolean

}
