package com.alexey_anufriev.scopes_manager.utils

import com.intellij.psi.search.scope.packageSet.CompoundPackageSet
import com.intellij.psi.search.scope.packageSet.PackageSet
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PackageSetUtilsTest {

    @Test
    fun `should exclude package root-level package`() {
        val emptyCompoundPackage = mock<CompoundPackageSet> {
            on { sets } doReturn emptyArray()
        }

        val remainingNestedPackage = mock<PackageSet> {
            on { text } doReturn "remaining.nested.pkg"
        }

        val compoundPackage = mock<CompoundPackageSet> {
            on { sets } doReturn arrayOf(remainingNestedPackage)
        }

        val excludedPackage = mock<PackageSet> {
            on { text } doReturn "excluded.pkg"
        }

        val remainingPackage = mock<PackageSet> {
            on { text } doReturn "remaining.pkg"
        }

        val rootPackage = mock<CompoundPackageSet> {
            on { sets } doReturn arrayOf(emptyCompoundPackage, excludedPackage, remainingPackage, compoundPackage)
        }

        val remainingPackages = PackageSetUtils.excludePackage(rootPackage, "excluded.pkg")

        assertThat(remainingPackages).hasSize(2)
        assertThat(remainingPackages).containsOnly(remainingPackage, remainingNestedPackage)
    }

    @Test
    fun `should exclude package nested package`() {
        val emptyCompoundPackage = mock<CompoundPackageSet> {
            on { sets } doReturn emptyArray()
        }

        val excludedNestedPackage = mock<PackageSet> {
            on { text } doReturn "excluded.nested.pkg"
        }

        val remainingNestedPackage = mock<PackageSet> {
            on { text } doReturn "remaining.nested.pkg"
        }

        val compoundPackage = mock<CompoundPackageSet> {
            on { sets } doReturn arrayOf(excludedNestedPackage, remainingNestedPackage)
        }

        val remainingPackage = mock<PackageSet> {
            on { text } doReturn "remaining.pkg"
        }

        val rootPackage = mock<CompoundPackageSet> {
            on { sets } doReturn arrayOf(emptyCompoundPackage, compoundPackage, remainingPackage)
        }

        val remainingPackages = PackageSetUtils.excludePackage(rootPackage, "excluded.nested.pkg")

        assertThat(remainingPackages).hasSize(2)
        assertThat(remainingPackages).containsOnly(remainingPackage, remainingNestedPackage)
    }

}
