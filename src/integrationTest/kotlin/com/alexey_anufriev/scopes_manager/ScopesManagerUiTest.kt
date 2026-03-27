package com.alexey_anufriev.scopes_manager

import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.ProjectViewToolWindowUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.io.File
import java.awt.event.KeyEvent
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ScopesManagerUiTest {

    init {
        di = DI {
            extend(di)
            bindSingleton<CIServer>(overrides = true) { failingCiServer() }
        }
    }

    @Test
    fun pluginStartsWithoutUiErrorsOnProjectOpen() {
        val productCode = System.getProperty("uiTestProductCode", "IC")
        val ideVersion = System.getProperty("uiTestIdeVersion")
        val ideChannel = System.getProperty("uiTestIdeChannel", "release")
        val toolWindowId = System.getProperty("uiTestToolWindowId", "Project")
        val testCase = TestCase(
            ideProduct(productCode),
            LocalProjectInfo(Path.of("src/integrationTest/resources/test-projects/simple-project"))
        )

        val ideUnderTest = when (ideChannel) {
            "eap" -> ideVersion?.let { testCase.useEAP(it) } ?: testCase.useEAP()
            else -> ideVersion?.let { testCase.withVersion(it) } ?: testCase.useRelease()
        }

        val testNameSuffix = listOf(productCode, ideVersion ?: ideChannel).joinToString("-")

        Starter.newContext(
            "scopes-manager-ui-smoke-$testNameSuffix",
            ideUnderTest
        ).apply {
            PluginConfigurator(this).installPluginFromFolder(File(System.getProperty("path.to.build.plugin")))
        }.runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)
            withContext(OnDispatcher.EDT) {
                getToolWindow(toolWindowId).show()
            }
            ideFrame {
                projectView {
                    assertAddToScopeVisibleForSampleFile()
                }
                popupMenu().findMenuItemByText("Add to Scope")
            }
        }
    }

    private fun ideProduct(productCode: String): IdeInfo = when (productCode) {
        "IC" -> IdeProductProvider.IC
        "IU" -> IdeProductProvider.IU
        "GO" -> IdeProductProvider.GO
        "RD" -> IdeProductProvider.RD
        "WS" -> IdeProductProvider.WS
        else -> throw IllegalArgumentException("Unsupported IDE product code: $productCode")
    }

    private fun failingCiServer(): CIServer = object : CIServer by NoCIServer {
        override fun reportTestFailure(
            testName: String,
            message: String,
            details: String,
            linkToLogs: String?
        ) {
            throw AssertionError("$testName failed: $message\n$details")
        }
    }

    private fun ProjectViewToolWindowUi.assertAddToScopeVisibleForSampleFile() {
        projectViewTree.expandAll(30.seconds)
        val expandedPaths = projectViewTree.collectExpandedPaths()
        val row = expandedPaths.firstOrNull { path ->
            path.path.contains("sample") && path.path.last() in setOf("App", "App.java")
        }?.row

        if (row != null && (openContextMenuWithRightClick(row) || openContextMenuWithKeyboard(row))) {
            return
        }

        throw AssertionError(
            "Could not find sample file in Project View. Expanded paths: ${expandedPaths.map { it.path }}"
        )
    }

    private fun ProjectViewToolWindowUi.openContextMenuWithRightClick(
        row: Int
    ): Boolean {
        return try {
            projectViewTree.rightClickRow(row)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun ProjectViewToolWindowUi.openContextMenuWithKeyboard(
        row: Int
    ): Boolean {
        return try {
            projectViewTree.clickRow(row)
            keyboard {
                hotKey(KeyEvent.VK_SHIFT, KeyEvent.VK_F10)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
