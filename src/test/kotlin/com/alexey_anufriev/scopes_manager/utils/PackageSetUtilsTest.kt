package com.alexey_anufriev.scopes_manager.utils

import com.intellij.psi.search.scope.packageSet.InvalidPackageSet
import com.intellij.psi.search.scope.packageSet.UnionPackageSet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PackageSetUtilsTest {

    @Test
    fun `should flatten compound package set after exclusion`() {
        val firstRootPackage = InvalidPackageSet("root.pkg1")
        val secondRootPackage = InvalidPackageSet("root.pkg2")
        val compoundRootPackage = UnionPackageSet.create(firstRootPackage, secondRootPackage) as UnionPackageSet

        val modifiedPackage = PackageSetUtils.excludePackage(compoundRootPackage, "root.pkg1")

        assertThat(modifiedPackage is InvalidPackageSet).isTrue
        assertThat((modifiedPackage as InvalidPackageSet).text).isEqualTo("root.pkg2")
    }

    @Test
    fun `should flatten nested compound package set after exclusion`() {
        val firstRootPackage = InvalidPackageSet("root.pkg1")
        val secondRootPackage = InvalidPackageSet("root.pkg2")

        val firstSubPackage = InvalidPackageSet("root.sub.pkg1")
        val secondSubPackage = InvalidPackageSet("root.sub.pkg2")
        val compoundSubPackage = UnionPackageSet.create(firstSubPackage, secondSubPackage)

        val compoundRootPackage = UnionPackageSet
            .create(firstRootPackage, secondRootPackage, compoundSubPackage) as UnionPackageSet

        val modifiedPackage = PackageSetUtils.excludePackage(compoundRootPackage, "root.sub.pkg1")

        assertThat(modifiedPackage is UnionPackageSet).isTrue
        assertThat((modifiedPackage as UnionPackageSet).text).isEqualTo("root.pkg1||root.pkg2||root.sub.pkg2")
    }

}
