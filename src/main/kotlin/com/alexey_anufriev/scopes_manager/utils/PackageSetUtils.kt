package com.alexey_anufriev.scopes_manager.utils

import com.intellij.psi.search.scope.packageSet.CompoundPackageSet
import com.intellij.psi.search.scope.packageSet.PackageSet
import com.intellij.util.containers.stream
import java.util.Arrays

object PackageSetUtils {

    fun excludePackage(rootPackage: CompoundPackageSet, packageToExclude: String): Array<PackageSet> {
        return Arrays.stream(rootPackage.sets)
            .filter { nestedPackage -> nestedPackage.text != packageToExclude }
            .map { nestedPackage ->
                if (nestedPackage is CompoundPackageSet) {
                    return@map excludePackage(nestedPackage, packageToExclude)
                }
                arrayOf(nestedPackage)
            }
            .flatMap { it.stream() }
            .toArray { size -> arrayOfNulls(size) }
    }

}
