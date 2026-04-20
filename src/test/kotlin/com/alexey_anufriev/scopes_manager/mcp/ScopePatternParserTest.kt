package com.alexey_anufriev.scopes_manager.mcp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScopePatternParserTest {

    @Test
    fun `should split pattern on double pipe`() {
        val result = ScopePatternParser.parse("src/a.ts||src/b.ts||src/c.ts")

        assertThat(result).containsExactly("src/a.ts", "src/b.ts", "src/c.ts")
    }

    @Test
    fun `should strip file project prefix`() {
        val result = ScopePatternParser.parse("file[proj]:src/a.ts||file[proj]:src/b.ts")

        assertThat(result).containsExactly("src/a.ts", "src/b.ts")
    }

    @Test
    fun `should strip other bracket-prefixed scopes`() {
        val result = ScopePatternParser.parse("src[module]:src/a.ts||lib[dep]:lib/b.ts")

        assertThat(result).containsExactly("src/a.ts", "lib/b.ts")
    }

    @Test
    fun `should convert double-slash star suffix to trailing slash`() {
        val result = ScopePatternParser.parse("file[proj]:src/a//*")

        assertThat(result).containsExactly("src/a/")
    }

    @Test
    fun `should convert single-slash star suffix to trailing slash`() {
        val result = ScopePatternParser.parse("file[proj]:src/a/*")

        assertThat(result).containsExactly("src/a/")
    }

    @Test
    fun `should leave bare file paths untouched`() {
        val result = ScopePatternParser.parse("file[proj]:src/app/module.ts")

        assertThat(result).containsExactly("src/app/module.ts")
    }

    @Test
    fun `should handle empty pattern`() {
        val result = ScopePatternParser.parse("")

        assertThat(result).isEmpty()
    }

    @Test
    fun `should ignore empty segments between separators`() {
        val result = ScopePatternParser.parse("file[proj]:src/a.ts||||file[proj]:src/b.ts")

        assertThat(result).containsExactly("src/a.ts", "src/b.ts")
    }
}
