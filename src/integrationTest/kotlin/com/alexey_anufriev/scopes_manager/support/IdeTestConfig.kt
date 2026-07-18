package com.alexey_anufriev.scopes_manager.support

import java.nio.file.Path

/** IDE products supported by the integration-test matrix. */
enum class IdeProduct(val code: String) {
    CLION("CL"),
    IDEA_COMMUNITY("IC"),
    IDEA_ULTIMATE("IU"),
    GOLAND("GO"),
    PYCHARM("PY"),
    RIDER("RD"),
    RUBYMINE("RM"),
    RUSTROVER("RR"),
    WEBSTORM("WS");

    companion object {
        /** Resolves an IDE product from its JetBrains product [code]. */
        fun fromCode(code: String): IdeProduct = entries.firstOrNull { it.code == code }
            ?: throw IllegalArgumentException("Unsupported IDE product code: $code")
    }
}

/** Distribution channels from which an IDE test build can be selected. */
enum class IdeChannel(val propertyValue: String) {
    RELEASE("release"),
    EAP("eap");

    companion object {
        /** Resolves an IDE channel from its Gradle property [value]. */
        fun fromProperty(value: String): IdeChannel = entries.firstOrNull { it.propertyValue == value }
            ?: throw IllegalArgumentException("Unsupported IDE channel: $value")
    }
}

/** Configuration needed to launch an IDE and locate its integration-test fixture. */
data class IdeTestConfig(
    val product: IdeProduct,
    val ideVersion: String?,
    val channel: IdeChannel,
    val toolWindowId: String,
    val projectHome: Path,
    val sampleFileNames: Set<String>,
    val samplePath: List<String>,
) {
    val testNameSuffix: String = listOf(product.code, ideVersion ?: channel.propertyValue).joinToString("-")
}

/** Loads [IdeTestConfig] values from JVM system properties. */
object IdeTestConfigLoader {
    /** Loads the integration-test configuration using [property] as the value source. */
    fun load(property: (String) -> String? = { System.getProperty(it) }): IdeTestConfig = IdeTestConfig(
        product = IdeProduct.fromCode(property("uiTestProductCode") ?: IdeProduct.IDEA_COMMUNITY.code),
        ideVersion = property("uiTestIdeVersion"),
        channel = IdeChannel.fromProperty(property("uiTestIdeChannel") ?: IdeChannel.RELEASE.propertyValue),
        toolWindowId = property("uiTestToolWindowId") ?: "Project",
        projectHome = Path.of(
            property("uiTestProjectPath")
                ?: "src/integrationTest/resources/test-projects/idea-project",
        ),
        sampleFileNames = property("uiTestSampleFileNames")
            ?.commaSeparatedValues()
            ?.toSet()
            ?: setOf("App", "App.java"),
        samplePath = property("uiTestSamplePath")
            ?.pathSegments()
            ?: listOf("src", "main", "java", "sample", "App"),
    )
}

private fun String.commaSeparatedValues(): List<String> =
    split(',').map(String::trim).filter(String::isNotEmpty)

private fun String.pathSegments(): List<String> =
    split('/').map(String::trim).filter(String::isNotEmpty)
