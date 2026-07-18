package com.alexey_anufriev.scopes_manager

import com.alexey_anufriev.scopes_manager.support.IdeChannel
import com.alexey_anufriev.scopes_manager.support.IdeProduct
import com.alexey_anufriev.scopes_manager.support.IdeTestConfigLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Path

class IdeTestConfigTest {

    @Test
    fun `loads default configuration`() {
        val config = IdeTestConfigLoader.load { null }

        assertEquals(IdeProduct.IDEA_COMMUNITY, config.product)
        assertEquals(IdeChannel.RELEASE, config.channel)
        assertEquals(null, config.ideVersion)
        assertEquals("Project", config.toolWindowId)
        assertEquals(Path.of("src/integrationTest/resources/test-projects/idea-project"), config.projectHome)
        assertEquals(setOf("App", "App.java"), config.sampleFileNames)
        assertEquals(listOf("src", "main", "java", "sample", "App"), config.samplePath)
        assertEquals("IC-release", config.testNameSuffix)
    }

    @Test
    fun `loads configured IDE and project fixture`() {
        val properties = mapOf(
            "uiTestProductCode" to "RD",
            "uiTestIdeVersion" to "2026.1",
            "uiTestIdeChannel" to "eap",
            "uiTestToolWindowId" to "Solution",
            "uiTestProjectPath" to "src/integrationTest/resources/test-projects/rider-project",
            "uiTestSampleFileNames" to " Program.cs, Program ",
            "uiTestSamplePath" to " App / Program.cs ",
        )

        val config = IdeTestConfigLoader.load(properties::get)

        assertEquals(IdeProduct.RIDER, config.product)
        assertEquals(IdeChannel.EAP, config.channel)
        assertEquals("2026.1", config.ideVersion)
        assertEquals("Solution", config.toolWindowId)
        assertEquals(setOf("Program.cs", "Program"), config.sampleFileNames)
        assertEquals(listOf("App", "Program.cs"), config.samplePath)
        assertEquals("RD-2026.1", config.testNameSuffix)
    }

    @Test
    fun `rejects unsupported product`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            IdeTestConfigLoader.load { name -> if (name == "uiTestProductCode") "UNKNOWN" else null }
        }

        assertEquals("Unsupported IDE product code: UNKNOWN", error.message)
    }

    @Test
    fun `rejects unsupported channel`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            IdeTestConfigLoader.load { name -> if (name == "uiTestIdeChannel") "nightly" else null }
        }

        assertEquals("Unsupported IDE channel: nightly", error.message)
    }
}
