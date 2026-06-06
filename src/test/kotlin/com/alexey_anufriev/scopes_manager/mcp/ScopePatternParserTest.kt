package com.alexey_anufriev.scopes_manager.mcp

import com.intellij.psi.search.scope.packageSet.ComplementPackageSet
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet
import com.intellij.psi.search.scope.packageSet.IntersectionPackageSet
import com.intellij.psi.search.scope.packageSet.InvalidPackageSet
import com.intellij.psi.search.scope.packageSet.PackageSet
import com.intellij.psi.search.scope.packageSet.UnionPackageSet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScopePatternParserTest {

    @Test
    fun `should unwrap single file pattern leaf`() {
        val set = filePattern("src/app/module.ts")

        assertThat(ScopePatternParser.parse(set)).containsExactly("src/app/module.ts")
    }

    @Test
    fun `should flatten union of file patterns`() {
        val set = UnionPackageSet.create(
            filePattern("src/a.ts"),
            filePattern("src/b.ts"),
            filePattern("src/c.ts"),
        )

        assertThat(ScopePatternParser.parse(set)).containsExactly("src/a.ts", "src/b.ts", "src/c.ts")
    }

    @Test
    fun `should convert double-slash star suffix to trailing slash`() {
        val set = filePattern("src/a//*")

        assertThat(ScopePatternParser.parse(set)).containsExactly("src/a/")
    }

    @Test
    fun `should convert single-slash star suffix to trailing slash`() {
        val set = filePattern("src/a/*")

        assertThat(ScopePatternParser.parse(set)).containsExactly("src/a/")
    }

    @Test
    fun `should mark complement entries with bang prefix`() {
        val set = UnionPackageSet.create(
            filePattern("src//*"),
            ComplementPackageSet(filePattern("src/test//*")),
        )

        assertThat(ScopePatternParser.parse(set)).containsExactly("src/", "!src/test/")
    }

    @Test
    fun `should flatten nested unions`() {
        val set = UnionPackageSet.create(
            UnionPackageSet.create(filePattern("a.ts"), filePattern("b.ts")),
            filePattern("c.ts"),
        )

        assertThat(ScopePatternParser.parse(set)).containsExactly("a.ts", "b.ts", "c.ts")
    }

    @Test
    fun `should fall back to text for intersection sets`() {
        val set = IntersectionPackageSet.create(
            filePattern("src//*"),
            filePattern("*.kt"),
        )

        assertThat(ScopePatternParser.parse(set))
            .singleElement()
            .asString()
            .contains("&&")
    }

    @Test
    fun `should deduplicate identical file patterns from different module roots when no resolver is given`() {
        val set = UnionPackageSet.create(
            FilePatternPackageSet("module.main", "com/foo/switch//*"),
            FilePatternPackageSet("module.test", "com/foo/switch//*"),
        )

        assertThat(ScopePatternParser.parse(set)).containsExactly("com/foo/switch/")
    }

    @Test
    fun `should resolve file patterns to physical paths via resolver`() {
        val set = UnionPackageSet.create(
            FilePatternPackageSet("proj.main", "kotlin/com/foo/switch//*"),
            FilePatternPackageSet("proj.test", "kotlin/com/foo/switch//*"),
            FilePatternPackageSet(null, "Makefile"),
        )
        val resolver: FilePatternResolver = { modulePattern, pattern ->
            when (modulePattern) {
                "proj.main" -> listOf("src/main/$pattern")
                "proj.test" -> listOf("src/test/$pattern")
                else -> listOf(pattern)
            }
        }

        assertThat(ScopePatternParser.parse(set, resolver)).containsExactly(
            "src/main/kotlin/com/foo/switch/",
            "src/test/kotlin/com/foo/switch/",
            "Makefile",
        )
    }

    @Test
    fun `should expand file pattern across multiple content roots from resolver`() {
        val set = FilePatternPackageSet("multi-root.main", "java/de/foo//*")
        val resolver: FilePatternResolver = { modulePattern, pattern ->
            if (modulePattern == "multi-root.main") {
                listOf("modules/foo/src/main/$pattern", "modules/foo/src/integrationTest/$pattern")
            } else {
                listOf(pattern)
            }
        }

        assertThat(ScopePatternParser.parse(set, resolver)).containsExactly(
            "modules/foo/src/main/java/de/foo/",
            "modules/foo/src/integrationTest/java/de/foo/",
        )
    }

    @Test
    fun `should fall back to text for invalid package sets`() {
        val set: PackageSet = InvalidPackageSet("broken pattern")

        assertThat(ScopePatternParser.parse(set)).containsExactly("broken pattern")
    }

    private fun filePattern(pattern: String): FilePatternPackageSet =
        FilePatternPackageSet(null, pattern)
}
