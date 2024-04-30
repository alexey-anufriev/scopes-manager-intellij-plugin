package com.alexey_anufriev.scopes_manager.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.scope.packageSet.CompoundPackageSet
import com.intellij.psi.search.scope.packageSet.IntersectionPackageSet
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.psi.search.scope.packageSet.PackageSetBase
import com.intellij.psi.search.scope.packageSet.UnionPackageSet
import java.util.Arrays
import java.util.stream.Collectors.toList
import javax.swing.Icon

object PackageSetUtils {

    fun excludePackage(rootPackage: CompoundPackageSet, packageToExclude: String): PackageSetBase {
        val filteredSet = Arrays.stream(rootPackage.sets)
            .filter { nestedSet -> nestedSet.text != packageToExclude }
            .map { nestedSet ->
                if (nestedSet is CompoundPackageSet) {
                    return@map excludePackage(nestedSet, packageToExclude)
                }
                return@map nestedSet
            }
            .collect(toList())
            .toTypedArray()

        return when (rootPackage) {
            is IntersectionPackageSet -> {
                IntersectionPackageSet.create(*filteredSet) as PackageSetBase
            }
            is UnionPackageSet -> {
                UnionPackageSet.create(*filteredSet) as PackageSetBase
            }
            else -> {
                rootPackage
            }
        }
    }

    fun contains(project: Project, packageSet: PackageSetBase, file: VirtualFile): Boolean {
        if (VfsUtilCore.isAncestor(project.baseDir, file, false)) {
            return packageSet.contains(file, project, null)
        }
        // external file (linked module)
        else {
            val fileIndex = ProjectRootManager.getInstance(project).fileIndex
            val module = fileIndex.getModuleForFile(file) ?: return false

            for (root in ModuleRootManager.getInstance(module).contentRoots) {
                if (VfsUtilCore.isAncestor(root!!, file, false)) {
                    val fileIsInScope = packageSet.contains(file, project, ExternalNamedScopeManager(root, project))
                    if (fileIsInScope) {
                        return true
                    }
                }
            }
            return false
        }
    }

}

// mock to be able to override base dir for package set matching logic
private class ExternalNamedScopeManager(
    private val projectBaseDir: VirtualFile,
    project: Project
) : NamedScopesHolder(project) {

    override fun getProjectBaseDir(): VirtualFile {
        return this.projectBaseDir
    }

    override fun getDisplayName(): String {
        TODO("Not intended for use")
    }

    override fun getIcon(): Icon {
        TODO("Not intended for use")
    }

}
