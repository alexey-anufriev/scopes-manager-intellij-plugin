package com.alexey_anufriev.scopes_manager.utils

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

}
