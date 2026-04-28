package com.alexey_anufriev.scopes_manager.mcp

import java.util.stream.Collectors.toList
import java.util.stream.Stream

object ScopePatternParser {

    fun parse(pattern: String): List<String> {
        val segments = pattern.split("||").toTypedArray()

        return Stream.of(*segments)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { stripPrefix(it) }
            .map { normalizeGlob(it) }
            .collect(toList())
    }

    private fun stripPrefix(segment: String): String {
        val bracket = segment.indexOf('[')
        val colon = segment.indexOf(':')
        return if (bracket in 0 until colon) segment.substring(colon + 1) else segment
    }

    private fun normalizeGlob(path: String): String {
        return when {
            path.endsWith("//*") -> path.dropLast(3) + "/"
            path.endsWith("/*") -> path.dropLast(2) + "/"
            else -> path
        }
    }

}
