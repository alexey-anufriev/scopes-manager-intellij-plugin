package com.alexey_anufriev.scopes_manager.actions.remove

import com.alexey_anufriev.scopes_manager.actions.ScopeActionBase
import com.alexey_anufriev.scopes_manager.utils.PackageSetUtils.excludePackage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.scope.packageSet.ComplementPackageSet
import com.intellij.psi.search.scope.packageSet.CompoundPackageSet
import com.intellij.psi.search.scope.packageSet.IntersectionPackageSet
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.psi.search.scope.packageSet.PackageSet
import com.intellij.psi.search.scope.packageSet.PackageSetBase
import com.intellij.psi.search.scope.packageSet.UnionPackageSet

class RemoveFromScopeAction(
    scopesHolder: NamedScopesHolder,
    scope: NamedScope
) : ScopeActionBase(scopesHolder, scope) {

    override fun getNewScopeContent(
        project: Project,
        selectedFile: VirtualFile,
        currentScopeContent: PackageSetBase?,
        selectedPackageContent: PackageSet
    ): PackageSetBase {
        var newScopeContent: PackageSetBase? = null
        if (currentScopeContent is CompoundPackageSet) {
            // as a fast-fix try to remove (if present) definition of the content that must be removed
            val remainingPackages = excludePackage(currentScopeContent, selectedPackageContent.text)
            newScopeContent = UnionPackageSet.create(*remainingPackages) as PackageSetBase
        }

        // if fast-fix did not help then exclude the content from the current scope
        if (newScopeContent == null || newScopeContent.contains(selectedFile, project, scopesHolder)) {
            newScopeContent = IntersectionPackageSet
                .create(currentScopeContent!!, ComplementPackageSet(selectedPackageContent)) as PackageSetBase
        }
        return newScopeContent
    }

    override fun skipAction(
        project: Project,
        selectedFile: VirtualFile,
        currentScopeContent: PackageSetBase?
    ): Boolean {
        return currentScopeContent == null || !currentScopeContent.contains(selectedFile, project, scopesHolder)
    }

}
