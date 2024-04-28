package com.alexey_anufriev.scopes_manager.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.scope.packageSet.CompoundPackageSet
import com.intellij.psi.search.scope.packageSet.IntersectionPackageSet
import com.intellij.psi.search.scope.packageSet.PackageSetBase
import com.intellij.psi.search.scope.packageSet.UnionPackageSet
import java.util.Arrays
import java.util.stream.Collectors.toList

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
                    val fileIsInScope = packageSet.contains(file, ExternalProject(root, project), null)
                    if (fileIsInScope) {
                        return true
                    }
                }
            }
            return false
        }
    }

}

private class ExternalProject(private val projectBaseDir: VirtualFile, projectDelegate: Project) : Project by projectDelegate {

    override fun getBaseDir(): VirtualFile {
        return this.projectBaseDir
    }

}