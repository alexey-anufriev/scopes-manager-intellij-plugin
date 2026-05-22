package com.alexey_anufriev.scopes_manager.mcp

import com.intellij.psi.search.scope.packageSet.ComplementPackageSet
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet
import com.intellij.psi.search.scope.packageSet.PackageSet
import com.intellij.psi.search.scope.packageSet.PatternBasedPackageSet
import com.intellij.psi.search.scope.packageSet.UnionPackageSet

typealias FilePatternResolver = (modulePattern: String?, pattern: String) -> List<String>

object ScopePatternParser {

    fun parse(
        set: PackageSet,
        resolveFilePattern: FilePatternResolver = { _, pattern -> listOf(pattern) },
    ): List<String> = buildList {
        collect(set, include = true, resolve = resolveFilePattern, into = this)
    }.distinct()

    private fun collect(
        set: PackageSet,
        include: Boolean,
        resolve: FilePatternResolver,
        into: MutableList<String>,
    ) {
        when (set) {
            is ComplementPackageSet -> collect(set.complementarySet, !include, resolve, into)
            is UnionPackageSet -> set.sets.forEach { collect(it, include, resolve, into) }
            is FilePatternPackageSet -> resolve(set.modulePattern, set.pattern).forEach { into += format(it, include) }
            is PatternBasedPackageSet -> into += format(set.pattern, include)
            else -> into += format(set.text, include)
        }
    }

    private fun format(path: String, include: Boolean): String {
        val normalized = when {
            path.endsWith("//*") -> path.dropLast(3) + "/"
            path.endsWith("/*") -> path.dropLast(2) + "/"
            else -> path
        }
        return if (include) normalized else "!$normalized"
    }

}
